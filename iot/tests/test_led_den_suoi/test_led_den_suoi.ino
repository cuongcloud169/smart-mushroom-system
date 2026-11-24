// ===== TEST 2 LED ĐÈN VÀ SƯỞI QUA RELAY (không cần Wi-Fi, không cần server) =====
// Đấu dây điều khiển:
//   IN relay đèn -> D26 (GPIO26), IN relay sưởi -> D25 (GPIO25), VCC relay -> 5V, GND relay -> GND
// Đấu LED qua tiếp điểm NO (mỗi LED 1 điện trở 220Ω):
//   5V -> COM relay đèn ; NO -> 220Ω -> chân DÀI LED đèn ; chân NGẮN -> GND
//   5V -> COM relay sưởi; NO -> 220Ω -> chân DÀI LED sưởi (đỏ) ; chân NGẮN -> GND
//
// Mở Serial Monitor 115200 baud, gõ:
//   d  -> đảo trạng thái LED đèn
//   s  -> đảo trạng thái LED sưởi
//   1  -> bật cả 2      0 -> tắt cả 2
//   a  -> tự động: đèn / sưởi / cả hai / tắt, mỗi bước 2 giây (gõ lại 'a' để dừng)

const int RELAY_LIGHT  = 26;
const int RELAY_HEATER = 25;
const bool RELAY_ACTIVE_LOW = true;   // relay SONGLE xanh: LOW = bật

bool lightOn = false, heaterOn = false;
bool autoMode = false;
int autoStep = 0;
unsigned long lastStep = 0;
const unsigned long STEP_MS = 2000;

void relayWrite(int pin, bool on) {
  digitalWrite(pin, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW);
}

void setLight(bool on)  { lightOn = on;  relayWrite(RELAY_LIGHT, on); }
void setHeater(bool on) { heaterOn = on; relayWrite(RELAY_HEATER, on); }

void printState() {
  Serial.printf("Den: %s | Suoi: %s\n", lightOn ? "BAT" : "TAT", heaterOn ? "BAT" : "TAT");
}

void setup() {
  Serial.begin(115200);
  pinMode(RELAY_LIGHT, OUTPUT);
  pinMode(RELAY_HEATER, OUTPUT);
  setLight(false);                    // khởi động luôn tắt
  setHeater(false);
  Serial.println("Go d = den, s = suoi, 1 = bat het, 0 = tat het, a = tu dong");
  printState();
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    bool changed = true;
    if (c == 'd')      { autoMode = false; setLight(!lightOn); }
    else if (c == 's') { autoMode = false; setHeater(!heaterOn); }
    else if (c == '1') { autoMode = false; setLight(true);  setHeater(true); }
    else if (c == '0') { autoMode = false; setLight(false); setHeater(false); }
    else if (c == 'a') {
      autoMode = !autoMode;
      autoStep = 0;
      lastStep = millis() - STEP_MS;
      Serial.println(autoMode ? "Che do tu dong: BAT" : "Che do tu dong: TAT");
      if (!autoMode) { setLight(false); setHeater(false); }
    }
    else changed = false;             // bỏ qua Enter / ký tự khác
    if (changed && !autoMode) printState();
  }

  if (autoMode && millis() - lastStep >= STEP_MS) {
    lastStep = millis();
    switch (autoStep) {
      case 0: setLight(true);  setHeater(false); break;
      case 1: setLight(false); setHeater(true);  break;
      case 2: setLight(true);  setHeater(true);  break;
      case 3: setLight(false); setHeater(false); break;
    }
    autoStep = (autoStep + 1) % 4;
    printState();
  }
}
