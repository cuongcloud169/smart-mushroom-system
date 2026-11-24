// ===== TEST MODULE MQ-135 TRÊN CHÂN D32 (không cần Wi-Fi) =====
// Đấu dây: VCC -> VIN (5V), GND -> GND,
//          AO -> điện trở -> D32, D32 -> điện trở -> GND (2 điện trở bằng nhau), DO để trống
// Mở Serial Monitor 115200: mỗi giây in số đọc ADC, điện áp tại D32 và ppm ước lượng.
// Thổi hơi / đưa cồn lại gần cảm biến -> số phải TĂNG rõ.

#define MQ_PIN 32

void setup() {
  Serial.begin(115200);
  analogReadResolution(12);                    // 0..4095
  analogSetPinAttenuation(MQ_PIN, ADC_11db);   // đo tới ~3.1V
  Serial.println("Test MQ-135 tren D32 - cho 1-3 phut de cam bien lam nong");
}

void loop() {
  long sum = 0;
  for (int i = 0; i < 10; i++) { sum += analogRead(MQ_PIN); delay(5); }
  int raw = sum / 10;
  float vPin = raw * 3.1 / 4095.0;             // điện áp tại chân D32
  float vAO  = vPin * 2;                       // điện áp thật ở AO (cầu chia đôi)
  int ppm = map(raw, 0, 4095, 400, 5000);

  Serial.printf("raw=%4d | D32=%.2fV | AO~%.2fV | ppm~%d", raw, vPin, vAO, ppm);
  if (raw < 50)        Serial.println("  <- gan 0V: AO chua noi vao D32 / sai cau chia ap / module chua co nguon");
  else if (raw > 4000) Serial.println("  <- gan max: thieu dien tro xuong GND hoac AO cam thang 5V");
  else                 Serial.println("  OK");
  delay(1000);
}
