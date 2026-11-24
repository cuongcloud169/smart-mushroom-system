# IoT – Bộ điều khiển ESP32

ESP32 đọc nhiệt độ, độ ẩm (DHT22) và chất lượng khí / CO2 ước lượng (MQ-135), gửi lên backend mỗi 2 giây, đồng thời nhận lệnh bật/tắt 4 thiết bị qua module relay 4 kênh.

## 1. Linh kiện

| Linh kiện | Ghi chú |
|---|---|
| ESP32 DevKit V1 (ESP32-WROOM-32, 30 chân) | Trong Arduino IDE chọn board **ESP32 Dev Module** |
| DHT22 (module 3 chân DAT, VCC, GND) | Nhiệt độ, độ ẩm |
| MQ-135 (module 4 chân AO, DO, GND, VCC) | Khí / CO2 **ước lượng**, nguồn 5V |
| Module relay 4 kênh SONGLE SRD-05VDC-SL-C | Kích mức **LOW**, tiếp điểm 10A 30VDC |
| Module phun sương 5V 1A, quạt 5V, 2 LED + điện trở 220Ω | LED mô phỏng đèn chiếu sáng và sưởi |
| 2 điện trở bằng nhau (1.5kΩ – 100kΩ) | Cầu chia áp cho chân AO của MQ-135 |
| Adapter 5V 2A | Cấp nguồn cho relay, MQ-135, phun sương, quạt |

## 2. Sơ đồ chân

| Thiết bị | Chân ESP32 |
|---|---|
| DHT22 – DAT | GPIO 4 (D4) |
| MQ-135 – AO (qua cầu chia áp) | GPIO 34 (D34) |
| Relay phun sương | GPIO 14 (D14) |
| Relay quạt | GPIO 27 (D27) |
| Relay đèn LED | GPIO 26 (D26) |
| Relay sưởi | GPIO 25 (D25) |

Nguồn:

- DHT22: VCC → **3V3** của ESP32.
- MQ-135, VCC của module relay, COM của các relay: **5V từ adapter**.
- **GND của adapter, ESP32, relay và cảm biến phải nối chung.**

Cầu chia áp MQ-135 (bắt buộc khi cấp 5V, vì chân ESP32 chỉ chịu tối đa 3.3V):

```
AO ──> R ──┬──> D34
           │
           R
           │
          GND
```

Thiết bị nối qua tiếp điểm **NO**: `5V → COM`, `NO → (+) thiết bị`, `(−) thiết bị → GND`. LED thì thêm điện trở 220Ω nối tiếp.

## 3. Nạp firmware

1. Arduino IDE → Boards Manager → cài **esp32 by Espressif Systems**.
2. Library Manager → cài **DHT sensor library** (Adafruit), **Adafruit Unified Sensor**, **ArduinoJson** (v7).
3. Mở `iot/firmware/firmware.ino`, sửa 4 dòng cấu hình:
   - `WIFI_SSID`, `WIFI_PASS`: Wi-Fi **2.4 GHz** (hotspot iPhone: bật *Tối đa hóa tính tương thích*).
   - `SERVER`: `http://<IP laptop chạy backend>:8080` (lấy IP bằng `ipconfig`, không dùng `localhost`).
   - `DEVICE_KEY`: Web → Thiết bị IoT & camera → nút **API key** của bộ điều khiển ESP32.
4. Chọn board **ESP32 Dev Module**, đúng cổng COM, bấm Upload. Nếu kẹt ở `Connecting...` thì giữ nút **BOOT**.
5. Serial Monitor 115200: thấy `HTTP 201` là đã gửi dữ liệu thành công.

**Không commit mật khẩu Wi-Fi và API key thật.**

## 4. Tùy chọn trong firmware

| Hằng số | Mặc định | Ý nghĩa |
|---|---|---|
| `HAS_CO2_SENSOR` | `true` | `false` nếu chưa lắp MQ-135 |
| `RELAY_ACTIVE_LOW` | `true` | Module relay bật khi chân ở mức LOW |
| `LED_DIRECT` | `false` | `true` nếu LED đèn/sưởi cắm thẳng vào GPIO 26/25 (không qua relay) |
| `LIGHT_HEATER_MANUAL_ONLY` | `false` | `true`: đèn và sưởi chỉ bật khi bấm bật tay trên web/app |
| `SEND_EVERY_MS` / `POLL_EVERY_MS` | 2000 / 1000 | Chu kỳ gửi dữ liệu / hỏi lệnh (DHT22 cần tối thiểu 2 giây) |

Giá trị CO2 từ MQ-135 là quy đổi tuyến tính 400–5000 ppm, chỉ mang tính ước lượng. Muốn đo CO2 chính xác cần cảm biến NDIR (MH-Z19B/C, SCD40).

## 5. Sketch kiểm tra từng phần (`iot/tests/`)

| Sketch | Kiểm tra |
|---|---|
| `test_mq135` | Số đọc ADC, điện áp chân D34 và ppm ước lượng của MQ-135 |
| `test_relay_phun_suong` | Bật/tắt relay phun sương bằng Serial (1 / 0 / a) |
| `test_led_den_suoi` | 2 LED đèn, sưởi qua relay (d / s / 1 / 0 / a) |
| `test_led_truc_tiep` | 2 LED cắm thẳng vào ESP32, tự chạy khi cấp điện |

`iot/examples/phun_suong_online` là bản rút gọn chỉ điều khiển phun sương qua server (DHT22 + 1 relay).

Không cần phần cứng có thể giả lập ESP32: `node tools/simulate-esp32.mjs <API_KEY> 5 http://localhost:8080`.

## 6. Xử lý lỗi

| Log Serial Monitor | Nguyên nhân / cách sửa |
|---|---|
| `that bai, se thu lai` | Sai tên/mật khẩu Wi-Fi hoặc mạng 5 GHz |
| `HTTP -1` | Không tới được backend: sai IP, Docker chưa chạy, Firewall chặn cổng 8080, khác mạng |
| `HTTP 401` | Sai API key |
| `Loi doc DHT22` | Kiểm tra dây DAT → D4, VCC → 3V3 |
| Không có `co2` trong dữ liệu gửi | D34 đọc gần 0V: kiểm tra dây AO và cầu chia áp (chạy `test_mq135`) |
| ESP32 tự khởi động lại khi bật thiết bị | Thiếu nguồn: dùng adapter 5V 2A riêng, nối chung GND |
