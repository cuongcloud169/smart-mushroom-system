// ===== FIRMWARE ESP32 - Bước 2: đọc DHT22 + kết nối Wi-Fi tự kết nối lại =====
// DHT22: chân DATA -> GPIO 4. Thư viện: "DHT sensor library" (Adafruit), "Adafruit Unified Sensor"

#include <WiFi.h>
#include <DHT.h>

// ---------- CẤU HÌNH ----------
const char* WIFI_SSID  = "TEN_WIFI";                      // ESP32 chỉ hỗ trợ Wi-Fi 2.4 GHz
const char* WIFI_PASS  = "MAT_KHAU_WIFI";

// ---------- CHÂN KẾT NỐI ----------
#define DHT_PIN 4

// ---------- THỜI GIAN ----------
const unsigned long SEND_EVERY_MS    = 10000;   // đọc cảm biến mỗi 10 giây
const unsigned long WIFI_TIMEOUT_MS  = 15000;   // chờ Wi-Fi tối đa 15 giây mỗi lần thử

DHT dht(DHT_PIN, DHT22);
unsigned long lastSend = 0;

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
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    if (isnan(t) || isnan(h)) Serial.println("Loi doc DHT22 (kiem tra day DATA)");
    else Serial.printf("Nhiet do: %.1f C | Do am: %.1f %%\n", t, h);
  }
}
