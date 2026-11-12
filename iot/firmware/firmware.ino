// ===== FIRMWARE ESP32 - Bước 1: đọc cảm biến nhiệt độ, độ ẩm DHT22 =====
// DHT22: chân DATA -> GPIO 4. Thư viện: "DHT sensor library" (Adafruit), "Adafruit Unified Sensor"
// Mở Serial Monitor (115200) để xem số đo.

#include <DHT.h>

// ---------- CHÂN KẾT NỐI ----------
#define DHT_PIN 4

DHT dht(DHT_PIN, DHT22);

void setup() {
  Serial.begin(115200);
  dht.begin();
}

void loop() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (isnan(t) || isnan(h)) {
    Serial.println("Loi doc DHT22 (kiem tra day DATA)");
  } else {
    Serial.printf("Nhiet do: %.1f C | Do am: %.1f %%\n", t, h);
  }
  delay(2000);   // DHT22 chỉ đọc được tối đa 1 lần / 2 giây
}
