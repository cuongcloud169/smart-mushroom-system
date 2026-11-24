// ===== 2 LED NHỎ THAY ĐÈN VÀ SƯỞI - CẮM THẲNG VÀO ESP32 (KHÔNG QUA RELAY) =====
// Đấu dây (mỗi LED 1 điện trở 220Ω, mã màu đỏ-đỏ-nâu):
//   D26 -> 220Ω -> chân DÀI LED đèn (trắng/vàng) ; chân NGẮN -> GND
//   D25 -> 220Ω -> chân DÀI LED sưởi (đỏ)        ; chân NGẮN -> GND
// Không cần nút bấm: cấp điện là tự chạy (AUTO_KHI_KHOI_DONG = true).
// Muốn bật/tắt tay: mở Serial Monitor 115200 baud, gõ
//   d = đảo LED đèn, s = đảo LED sưởi, 1 = bật cả 2, 0 = tắt cả 2, a = bật/tắt chế độ tự động

const int LED_LIGHT  = 26;            // thay đèn chiếu sáng
const int LED_HEATER = 25;            // thay sưởi
const bool AUTO_KHI_KHOI_DONG = true; // đổi false nếu chỉ muốn điều khiển bằng Serial

bool lightOn = false, heaterOn = false;
bool autoMode = AUTO_KHI_KHOI_DONG;
int autoStep = 0;
unsigned long lastStep = 0;
const unsigned long STEP_MS = 2000;

// LED nối GPIO -> LED -> GND nên HIGH = sáng
void setLight(bool on)  { lightOn = on;  digitalWrite(LED_LIGHT,  on ? HIGH : LOW); }
void setHeater(bool on) { heaterOn = on; digitalWrite(LED_HEATER, on ? HIGH : LOW); }

void printState() {
  Serial.printf("Den: %s | Suoi: %s\n", lightOn ? "BAT" : "TAT", heaterOn ? "BAT" : "TAT");
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_LIGHT, OUTPUT);
  pinMode(LED_HEATER, OUTPUT);
  setLight(false);
  setHeater(false);
  Serial.println("Go d = den, s = suoi, 1 = bat het, 0 = tat het, a = tu dong");
  lastStep = millis() - STEP_MS;      // chế độ tự động chạy bước đầu ngay
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
    else changed = false;
    if (changed && !autoMode) printState();
  }

  if (autoMode && millis() - lastStep >= STEP_MS) {
    lastStep = millis();
    switch (autoStep) {
      case 0: setLight(true);  setHeater(false); break;   // chỉ đèn
      case 1: setLight(false); setHeater(true);  break;   // chỉ sưởi
      case 2: setLight(true);  setHeater(true);  break;   // cả hai
      case 3: setLight(false); setHeater(false); break;   // tắt
    }
    autoStep = (autoStep + 1) % 4;
    printState();
  }
}
