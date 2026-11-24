// ===== TEST RIÊNG RELAY MÁY PHUN SƯƠNG (không cần Wi-Fi, không cần server) =====
// Đấu dây: chân IN của relay phun sương -> D14 (GPIO14), VCC relay -> 5V (VIN), GND relay -> GND
// Mở Serial Monitor (115200 baud), gõ:
//   1  -> BẬT phun sương
//   0  -> TẮT phun sương
//   a  -> chế độ tự động: bật 5 giây, tắt 5 giây (gõ lại 'a' để dừng)

const int RELAY_MIST = 14;            // relay phun sương
const bool RELAY_ACTIVE_LOW = false;  // module đang dùng: HIGH = bật

bool mistOn = false;
bool autoMode = false;
unsigned long lastToggle = 0;
const unsigned long AUTO_MS = 5000;

void setMist(bool on) {
  mistOn = on;
  digitalWrite(RELAY_MIST, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW);
  Serial.println(on ? "Phun suong: BAT" : "Phun suong: TAT");
}

void setup() {
  Serial.begin(115200);
  pinMode(RELAY_MIST, OUTPUT);
  setMist(false);                     // khởi động luôn tắt
  Serial.println("Go 1 = bat, 0 = tat, a = tu dong bat/tat 5s");
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == '1')      { autoMode = false; setMist(true); }
    else if (c == '0') { autoMode = false; setMist(false); }
    else if (c == 'a') {
      autoMode = !autoMode;
      Serial.println(autoMode ? "Che do tu dong: BAT" : "Che do tu dong: TAT");
      if (!autoMode) setMist(false);
      lastToggle = millis();
    }
  }

  if (autoMode && millis() - lastToggle >= AUTO_MS) {
    lastToggle = millis();
    setMist(!mistOn);
  }
}
