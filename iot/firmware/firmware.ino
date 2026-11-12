// ===== FIRMWARE ESP32: đọc cảm biến -> gửi lên server -> nhận lệnh bật/tắt thiết bị =====
// Cảm biến (đề yêu cầu nhiệt độ, độ ẩm và CO2):
//   - DHT22  (nhiệt độ + độ ẩm)   : chân DATA -> GPIO 4
//   - MH-Z19B (CO2, UART, nguồn 5V): TX cảm biến -> GPIO 16 (RX2), RX cảm biến -> GPIO 17 (TX2)
// Thiết bị điều khiển qua module relay: phun sương, quạt, đèn LED, sưởi (GPIO 25, 26, 27, 14)
// Thư viện (Arduino IDE > Library Manager): "DHT sensor library" (Adafruit), "Adafruit Unified Sensor", "ArduinoJson" (v7)
//
// Luồng hoạt động:
//   - Mỗi 10 giây: đọc cảm biến, POST /api/iot/readings (header x-device-key), áp dụng lệnh server trả về.
//   - Mỗi 3 giây : GET /api/iot/commands để lệnh bật/tắt tay từ web đến nhanh.
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
#define DHT_PIN 4
const int RELAY_MIST = 25, RELAY_FAN = 26, RELAY_LIGHT = 27, RELAY_HEATER = 14;
const bool RELAY_ACTIVE_LOW = true;    // đa số module relay bật khi chân ở mức LOW; đổi false nếu ngược lại
const bool HAS_CO2_SENSOR = true;      // đặt false nếu chưa lắp MH-Z19B

// ---------- THỜI GIAN ----------
const unsigned long SEND_EVERY_MS    = 10000;   // gửi dữ liệu mỗi 10 giây
const unsigned long POLL_EVERY_MS    = 3000;    // hỏi lệnh mỗi 3 giây
const unsigned long WIFI_TIMEOUT_MS  = 15000;   // chờ Wi-Fi tối đa 15 giây mỗi lần thử
const unsigned long FAILSAFE_MS      = 120000;  // mất server 2 phút -> tắt hết relay
const int HTTP_RETRIES = 3;

DHT dht(DHT_PIN, DHT22);
HardwareSerial co2Serial(2);           // UART2 cho MH-Z19B
unsigned long lastSend = 0, lastPoll = 0, lastServerOk = 0;

// ---------- RELAY ----------
void relayWrite(int pin, bool on) {
  digitalWrite(pin, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW);
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
}

// Đọc JSON {"commands":[{"kind":"mist","state":true}, ...]} và bật/tắt relay
void applyCommands(const String& json) {
  JsonDocument res;
  if (deserializeJson(res, json)) return;
  for (JsonObject c : res["commands"].as<JsonArray>()) {
    applyCommand(c["kind"], c["state"] | false);
  }
  lastServerOk = millis();
}

// ---------- CẢM BIẾN CO2 ----------
// Đọc CO2 (ppm) từ MH-Z19B. Trả -1 nếu lỗi. Cảm biến cần làm nóng ~3 phút sau khi cấp nguồn.
int readCO2() {
  if (!HAS_CO2_SENSOR) return -1;
  const byte cmd[9] = {0xFF, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79};
  byte res[9];
  while (co2Serial.available()) co2Serial.read();   // xóa dữ liệu cũ
  co2Serial.write(cmd, 9);
  if (co2Serial.readBytes(res, 9) != 9 || res[0] != 0xFF || res[1] != 0x86) return -1;
  byte sum = 0;
  for (int i = 1; i < 8; i++) sum += res[i];
  if ((byte)(0xFF - sum + 1) != res[8]) return -1;   // sai checksum
  return res[2] * 256 + res[3];
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
  int co2 = readCO2();
  if (isnan(t) || isnan(h)) {
    Serial.println("Loi doc DHT22 (kiem tra day DATA)");
    if (co2 < 0) return;     // không có số đo nào hợp lệ
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
  co2Serial.begin(9600, SERIAL_8N1, 16, 17);
  co2Serial.setTimeout(1000);
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
