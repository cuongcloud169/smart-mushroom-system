// ===== ESP32 CHỈ ĐIỀU KHIỂN MÁY PHUN SƯƠNG QUA SERVER =====
// Tách từ firmware đầy đủ: giữ DHT22 (độ ẩm quyết định phun sương tự động),
// bỏ cảm biến khí và các relay quạt / đèn / sưởi.
// Đấu dây:
//   DHT22 : DAT -> D4, VCC -> 3V3, GND -> GND
//   Relay : IN (phun sương) -> D14, VCC -> 5V, GND -> GND
// Thư viện: "DHT sensor library" (Adafruit), "Adafruit Unified Sensor", "ArduinoJson" (v7)

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// ---------- CẤU HÌNH: sửa 4 dòng này ----------
const char* WIFI_SSID  = "TEN_WIFI";                      // chỉ Wi-Fi 2.4 GHz
const char* WIFI_PASS  = "MAT_KHAU_WIFI";
const char* SERVER     = "http://192.168.1.10:8080";      // IP LAN máy chạy backend
const char* DEVICE_KEY = "DAN_API_KEY_CUA_ESP32";         // Web > Thiết bị & camera > API key

// ---------- CHÂN ----------
#define DHT_PIN 4
const int RELAY_MIST = 14;            // relay phun sương
const bool RELAY_ACTIVE_LOW = true;

// ---------- THỜI GIAN ----------
const unsigned long SEND_EVERY_MS   = 10000;   // gửi độ ẩm mỗi 10 giây
const unsigned long POLL_EVERY_MS   = 3000;    // hỏi lệnh mỗi 3 giây
const unsigned long WIFI_TIMEOUT_MS = 15000;
const unsigned long FAILSAFE_MS     = 120000;  // mất server 2 phút -> tắt phun sương

DHT dht(DHT_PIN, DHT22);
unsigned long lastSend = 0, lastPoll = 0, lastServerOk = 0;
bool mistOn = false;

// ---------- RELAY PHUN SƯƠNG ----------
void setMist(bool on) {
  if (on != mistOn) Serial.println(on ? ">> Phun suong BAT" : ">> Phun suong TAT");
  mistOn = on;
  digitalWrite(RELAY_MIST, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW);
}

// Đọc {"commands":[{"kind":"mist","state":true}, ...]}, chỉ lấy lệnh "mist"
void applyCommands(const String& json) {
  JsonDocument res;
  if (deserializeJson(res, json)) return;
  for (JsonObject c : res["commands"].as<JsonArray>()) {
    const char* kind = c["kind"];
    if (kind && !strcmp(kind, "mist")) setMist(c["state"] | false);
  }
  lastServerOk = millis();
}

// ---------- WI-FI ----------
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

// ---------- GỬI NHIỆT ĐỘ / ĐỘ ẨM ----------
void sendReading() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (isnan(t) || isnan(h)) {
    Serial.println("Loi doc DHT22 (kiem tra day DATA)");
    return;
  }
  JsonDocument doc;
  doc["temperature"] = round(t * 10) / 10.0;
  doc["humidity"]    = round(h * 10) / 10.0;
  String body;
  serializeJson(doc, body);

  HTTPClient http;
  http.setTimeout(5000);
  http.begin(String(SERVER) + "/api/iot/readings");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-device-key", DEVICE_KEY);
  int code = http.POST(body);
  Serial.printf("Gui %s -> HTTP %d\n", body.c_str(), code);
  if (code == 201) applyCommands(http.getString());
  else if (code == 401) Serial.println("Sai API key!");
  http.end();
}

// ---------- HỎI LỆNH BẬT/TẮT TAY TỪ WEB ----------
void pollCommands() {
  HTTPClient http;
  http.setTimeout(3000);
  http.begin(String(SERVER) + "/api/iot/commands");
  http.addHeader("x-device-key", DEVICE_KEY);
  if (http.GET() == 200) applyCommands(http.getString());
  http.end();
}

void setup() {
  Serial.begin(115200);
  pinMode(RELAY_MIST, OUTPUT);
  mistOn = true;          // ép in log lần đầu
  setMist(false);         // khởi động luôn tắt
  dht.begin();
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
  // An toàn: mất server quá lâu thì tắt phun sương
  if (millis() - lastServerOk > FAILSAFE_MS) {
    setMist(false);
    lastServerOk = millis();
    Serial.println("Mat ket noi server qua lau -> tat phun suong");
  }
}
