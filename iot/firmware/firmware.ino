// ===== FIRMWARE ESP32: đọc cảm biến -> gửi lên server -> nhận lệnh bật/tắt thiết bị =====
// Cảm biến (đề yêu cầu nhiệt độ, độ ẩm và CO2):
//   - DHT22  (nhiệt độ + độ ẩm)   : chân DATA -> GPIO 33
//   - MQ-135 (khí / CO2 ước lượng, nguồn 5V): AO -> cầu chia áp 2 điện trở bằng nhau -> GPIO 32, DO để trống
//   Analog chỉ đọc được trên ADC1 (GPIO 32-36, 39) khi bật Wi-Fi; ADC2 (0, 2, 4, 12-15, 25-27) bị Wi-Fi chiếm
// Thiết bị điều khiển qua module relay: phun sương GPIO 14, quạt GPIO 27, đèn LED GPIO 26, sưởi GPIO 25
// Thư viện (Arduino IDE > Library Manager): "DHT sensor library" (Adafruit), "Adafruit Unified Sensor", "ArduinoJson" (v7)
//
// Luồng hoạt động:
//   - Mỗi 2 giây : đọc cảm biến, POST /api/iot/readings (header x-device-key), áp dụng lệnh server trả về.
//   - Mỗi 1 giây : GET /api/iot/commands để lệnh bật/tắt tay từ web đến nhanh.
//   - Mất Wi-Fi  : tự kết nối lại (không treo), relay giữ trạng thái an toàn (tắt) nếu mất server quá lâu.

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// ---------- CẤU HÌNH: sửa 4 dòng này ----------
const char* WIFI_SSID  = "TEN_WIFI";                      // ESP32 chỉ hỗ trợ Wi-Fi 2.4 GHz
const char* WIFI_PASS  = "MAT_KHAU_WIFI";
const char* SERVER     = "http://192.168.1.10:8080";      // IP LAN của máy chạy backend (không dùng localhost)
const char* DEVICE_KEY = "DAN_API_KEY_CUA_ESP32";         // Web > Thiết bị & camera > API key

// ---------- CHÂN KẾT NỐI ----------
#define DHT_PIN 33                      // D33
const int RELAY_MIST = 14, RELAY_FAN = 27, RELAY_LIGHT = 26, RELAY_HEATER = 25;
const bool RELAY_ACTIVE_LOW = false;   // module đang dùng bật khi chân ở mức HIGH; đổi true nếu thiết bị chạy ngược
const bool LED_DIRECT = false;         // true nếu LED đèn/sưởi cắm THẲNG vào GPIO 26/25 (không qua relay)
const bool HAS_CO2_SENSOR = true;      // đặt false nếu chưa lắp MQ-135
#define MQ_PIN 32                      // D32 (ADC1). Không dùng chân ADC2 vì bị Wi-Fi chiếm

// ---------- QUY ĐỔI MQ-135 ----------
// MQ-135 đo chung nhiều loại khí, giá trị chỉ là ƯỚC LƯỢNG: quy đổi tuyến tính ADC 0..4095 -> 400..5000 ppm
const int MQ_RAW_MIN = 50;             // dưới mức này coi như chưa cắm dây AO
const int CO2_MIN_PPM = 400, CO2_MAX_PPM = 5000;

// ---------- THỜI GIAN ----------
const unsigned long SEND_EVERY_MS    = 2000;    // gửi dữ liệu mỗi 2 giây (DHT22 cần tối thiểu 2 giây)
const unsigned long POLL_EVERY_MS    = 1000;    // hỏi lệnh mỗi 1 giây
const unsigned long WIFI_TIMEOUT_MS  = 15000;   // chờ Wi-Fi tối đa 15 giây mỗi lần thử
const unsigned long FAILSAFE_MS      = 120000;  // mất server 2 phút -> tắt hết relay
const int HTTP_RETRIES = 3;

DHT dht(DHT_PIN, DHT22);
unsigned long lastSend = 0, lastPoll = 0, lastServerOk = 0;

// ---------- RELAY ----------
void relayWrite(int pin, bool on) {
  bool activeLow = RELAY_ACTIVE_LOW;
  if (LED_DIRECT && (pin == RELAY_LIGHT || pin == RELAY_HEATER)) activeLow = false;  // LED nối GPIO -> LED -> GND: HIGH = sáng
  digitalWrite(pin, (on ^ activeLow) ? HIGH : LOW);
}

void allRelaysOff() {
  int pins[] = {RELAY_MIST, RELAY_FAN, RELAY_LIGHT, RELAY_HEATER};
  for (int p : pins) relayWrite(p, false);
}

// kind = mist | fan | light | heater
void applyCommand(const char* kind, bool on) {
  if (!kind) return;
  if (!strcmp(kind, "mist"))        relayWrite(RELAY_MIST, on);
  else if (!strcmp(kind, "fan"))    relayWrite(RELAY_FAN, on);
  else if (!strcmp(kind, "light"))  relayWrite(RELAY_LIGHT, on);
  else if (!strcmp(kind, "heater")) relayWrite(RELAY_HEATER, on);
  Serial.printf("  Lenh: %s -> %s\n", kind, on ? "BAT" : "TAT");
}

// true: đèn và sưởi chỉ BẬT khi có lệnh bật tay (mode = "manual"); lệnh tự động bị bỏ qua -> giữ tắt
// false: đèn và sưởi chạy theo cả lệnh tự động lẫn lệnh tay
const bool LIGHT_HEATER_MANUAL_ONLY = false;

// Đọc JSON {"commands":[{"kind":"mist","state":true,"mode":"auto"}, ...]} và bật/tắt relay
void applyCommands(const String& json) {
  JsonDocument res;
  if (deserializeJson(res, json)) return;
  for (JsonObject c : res["commands"].as<JsonArray>()) {
    const char* kind = c["kind"];
    bool on = c["state"] | false;
    const char* mode = c["mode"] | "auto";
    if (LIGHT_HEATER_MANUAL_ONLY && kind &&
        (!strcmp(kind, "light") || !strcmp(kind, "heater")) && strcmp(mode, "manual") != 0) {
      on = false;
    }
    applyCommand(kind, on);
  }
  lastServerOk = millis();
}

// ---------- CẢM BIẾN KHÍ MQ-135 ----------
// Lấy trung bình 10 lần đọc ở chân analog, trả ppm ước lượng hoặc -1 nếu lỗi.
// Cảm biến cần làm nóng 1-3 phút sau khi cấp nguồn thì số đo mới ổn định.
int readCO2() {
  if (!HAS_CO2_SENSOR) return -1;
  long sum = 0;
  for (int i = 0; i < 10; i++) { sum += analogRead(MQ_PIN); delay(5); }
  int raw = sum / 10;                           // 0..4095
  if (raw < MQ_RAW_MIN) {
    Serial.printf("MQ-135 doc raw=%d (gan 0V) -> kiem tra day AO, cau chia ap, chan D32\n", raw);
    return -1;
  }
  return map(raw, 0, 4095, CO2_MIN_PPM, CO2_MAX_PPM);
}

// ---------- WI-FI ----------
// Thử kết nối trong WIFI_TIMEOUT_MS, không chặn mãi; loop() sẽ thử lại lần sau
bool ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) return true;
  Serial.print("Dang ket noi WiFi");
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < WIFI_TIMEOUT_MS) {
    delay(500);
    Serial.print(".");
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println(" OK, IP: " + WiFi.localIP().toString());
    return true;
  }
  Serial.println(" that bai, se thu lai");
  return false;
}

// ---------- GỬI DỮ LIỆU ----------
void sendReading() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  // DHT22 hở dây thường trả 0°C và 0% (vẫn đúng checksum) -> coi như lỗi, không gửi
  if ((t == 0 && h == 0) || t < -10 || t > 60 || h <= 0 || h > 100) { t = NAN; h = NAN; }
  int co2 = readCO2();
  if (isnan(t) || isnan(h)) {
    Serial.println("Loi doc DHT22 (kiem tra day DAT -> D33, VCC -> 3V3, GND)");
    if (co2 < 0) { Serial.println("Khong co so do hop le -> bo qua lan gui nay"); return; }
  }

  JsonDocument doc;
  if (!isnan(t)) doc["temperature"] = round(t * 10) / 10.0;
  if (!isnan(h)) doc["humidity"] = round(h * 10) / 10.0;
  if (co2 >= 0) doc["co2"] = co2;
  String body;
  serializeJson(doc, body);

  for (int attempt = 1; attempt <= HTTP_RETRIES; attempt++) {
    HTTPClient http;
    http.setTimeout(5000);
    http.begin(String(SERVER) + "/api/iot/readings");
    http.addHeader("Content-Type", "application/json");
    http.addHeader("x-device-key", DEVICE_KEY);
    int code = http.POST(body);
    Serial.printf("Gui %s -> HTTP %d (lan %d)\n", body.c_str(), code, attempt);
    if (code == 201) {
      applyCommands(http.getString());
      http.end();
      return;
    }
    if (code == 400 || code == 401) {   // dữ liệu sai hoặc sai API key: gửi lại cũng vô ích
      Serial.println(http.getString());
      http.end();
      return;
    }
    http.end();
    delay(1000);
  }
}

// ---------- HỎI LỆNH ----------
void pollCommands() {
  HTTPClient http;
  http.setTimeout(3000);
  http.begin(String(SERVER) + "/api/iot/commands");
  http.addHeader("x-device-key", DEVICE_KEY);
  int code = http.GET();
  if (code == 200) applyCommands(http.getString());
  http.end();
}

void setup() {
  Serial.begin(115200);
  int pins[] = {RELAY_MIST, RELAY_FAN, RELAY_LIGHT, RELAY_HEATER};
  for (int p : pins) pinMode(p, OUTPUT);
  allRelaysOff();                                   // mặc định tắt hết khi khởi động
  dht.begin();
  analogReadResolution(12);                         // ADC 0..4095
  analogSetPinAttenuation(MQ_PIN, ADC_11db);        // đo được tới ~3.1V
  WiFi.mode(WIFI_STA);
  ensureWifi();
  lastServerOk = millis();
}

void loop() {
  if (!ensureWifi()) {
    delay(2000);
  } else {
    unsigned long now = millis();
    if (now - lastSend >= SEND_EVERY_MS) {
      lastSend = now;
      sendReading();
    } else if (now - lastPoll >= POLL_EVERY_MS) {
      lastPoll = now;
      pollCommands();
    }
  }
  // An toàn: mất liên lạc server quá lâu thì tắt hết thiết bị
  if (millis() - lastServerOk > FAILSAFE_MS) {
    allRelaysOff();
    lastServerOk = millis();
    Serial.println("Mat ket noi server qua lau -> tat het relay");
  }
}
