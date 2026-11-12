// ===== FIRMWARE ESP32 - Bước 3: gửi dữ liệu cảm biến lên Java Backend =====
// DHT22: chân DATA -> GPIO 4
// Thư viện: "DHT sensor library" (Adafruit), "Adafruit Unified Sensor", "ArduinoJson" (v7)
// Mỗi 10 giây: đọc cảm biến, POST /api/iot/readings kèm header x-device-key (xác thực thiết bị).

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

// ---------- THỜI GIAN ----------
const unsigned long SEND_EVERY_MS    = 10000;   // gửi dữ liệu mỗi 10 giây
const unsigned long WIFI_TIMEOUT_MS  = 15000;   // chờ Wi-Fi tối đa 15 giây mỗi lần thử
const int HTTP_RETRIES = 3;

DHT dht(DHT_PIN, DHT22);
unsigned long lastSend = 0;

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

// ---------- GỬI DỮ LIỆU ----------
void sendReading() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (isnan(t) || isnan(h)) {
    Serial.println("Loi doc DHT22 (kiem tra day DATA)");
    return;
  }

  JsonDocument doc;
  doc["temperature"] = round(t * 10) / 10.0;
  doc["humidity"] = round(h * 10) / 10.0;
  String body;
  serializeJson(doc, body);

  // Gửi lại tối đa 3 lần nếu lỗi mạng / server
  for (int attempt = 1; attempt <= HTTP_RETRIES; attempt++) {
    HTTPClient http;
    http.setTimeout(5000);
    http.begin(String(SERVER) + "/api/iot/readings");
    http.addHeader("Content-Type", "application/json");
    http.addHeader("x-device-key", DEVICE_KEY);
    int code = http.POST(body);
    Serial.printf("Gui %s -> HTTP %d (lan %d)\n", body.c_str(), code, attempt);
    if (code == 201 || code == 400 || code == 401) {   // thành công, hoặc lỗi dữ liệu/API key: không gửi lại
      if (code != 201) Serial.println(http.getString());
      http.end();
      return;
    }
    http.end();
    delay(1000);
  }
}

void setup() {
  Serial.begin(115200);
  dht.begin();
  WiFi.mode(WIFI_STA);
  ensureWifi();
}

void loop() {
  if (!ensureWifi()) {
    delay(2000);
    return;
  }
  if (millis() - lastSend >= SEND_EVERY_MS) {
    lastSend = millis();
    sendReading();
  }
}
