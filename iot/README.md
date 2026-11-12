# IoT – Bộ điều khiển ESP32 cho phòng trồng nấm

Thư mục này chứa firmware cho ESP32: đo nhiệt độ, độ ẩm, CO2 trong phòng trồng nấm, gửi dữ liệu lên backend và bật/tắt phun sương, quạt, đèn, sưởi theo lệnh từ server.

## 1. Thiết bị

| Thiết bị | Số lượng | Công dụng |
|---|---|---|
| ESP32 DevKit V1 | 1 | Bộ điều khiển chính, kết nối Wi-Fi |
| DHT22 | 1 | Đo nhiệt độ, độ ẩm |
| MH-Z19B (hoặc MQ135) | 1 | Đo / ước lượng nồng độ CO2 |
| Module relay 4 kênh 5V | 1 | Bật/tắt 4 thiết bị |
| Phun sương, quạt, đèn, sưởi | mỗi loại 1 | Thiết bị chấp hành |
| Nguồn 12V + mạch hạ áp LM2596 (12V → 5V) | 1 | Cấp điện cho toàn mạch |

## 2. Sơ đồ chân

| Thiết bị | Chân ESP32 |
|---|---|
| DHT22 DATA | GPIO 4 (nguồn 3.3V) |
| MH-Z19B TX / RX | GPIO 16 (RX2) / GPIO 17 (TX2), nguồn 5V |
| Relay IN1 – Phun sương | GPIO 25 |
| Relay IN2 – Quạt | GPIO 26 |
| Relay IN3 – Đèn | GPIO 27 |
| Relay IN4 – Sưởi | GPIO 14 |

- Tất cả GND nối chung.
- Relay kích mức thấp (LOW = bật). Nếu module của bạn ngược lại, đổi `RELAY_ACTIVE_LOW = false` trong firmware.
- Vặn LM2596 ra đúng 5V trước khi nối vào ESP32.

## 3. Nạp firmware

1. Cài **Arduino IDE**, thêm board ESP32: *File > Preferences > Additional Boards Manager URLs* nhập `https://espressif.github.io/arduino-esp32/package_esp32_index.json`, rồi cài **esp32** trong *Boards Manager*.
2. Cài thư viện **DHT sensor library** (Adafruit) trong *Library Manager*.
3. Mở `iot/firmware/firmware.ino`, sửa phần cấu hình:

```cpp
const char* WIFI_SSID  = "TEN_WIFI";                 // chỉ hỗ trợ Wi-Fi 2.4 GHz
const char* WIFI_PASS  = "MAT_KHAU_WIFI";
const char* SERVER     = "http://192.168.1.10:8080"; // IP LAN của máy chạy backend, không dùng localhost
const char* DEVICE_KEY = "DAN_API_KEY_CUA_ESP32";    // API key của thiết bị trên web
const bool HAS_CO2_SENSOR = true;                     // false nếu chưa lắp cảm biến CO2
```

4. Chọn board **ESP32 Dev Module**, chọn cổng COM, bấm **Upload**.
5. Mở *Serial Monitor* (115200 baud) để xem ESP32 kết nối Wi-Fi và gửi dữ liệu.

> Không commit mật khẩu Wi-Fi thật lên GitHub: chỉ sửa trên máy khi nạp.

## 4. Cách hoạt động

- Mỗi **10 giây**: đọc cảm biến, gửi `POST /api/iot/readings` (header `x-device-key`), áp dụng lệnh server trả về.
- Mỗi **3 giây**: gọi `GET /api/iot/commands` để nhận lệnh bật/tắt tay từ web.
- Mất Wi-Fi: tự kết nối lại, không treo chương trình.
- Gửi lỗi: thử lại tối đa 3 lần.
- Mất kết nối server quá **2 phút**: tự tắt hết relay để an toàn.

## 5. Kiểm thử khi chưa có phần cứng

Script `tools/simulate-esp32.mjs` giả lập ESP32, gửi nhiệt độ, độ ẩm, CO2 ngẫu nhiên lên server (cần Node.js 18+):

```bash
node tools/simulate-esp32.mjs <API_KEY> 5 http://localhost:8080
```

- Tham số 1: API key của thiết bị.
- Tham số 2: số giây giữa các lần gửi (mặc định 5).
- Tham số 3: địa chỉ backend (mặc định `http://localhost:8080`).

## 6. Kịch bản kiểm tra

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| 1 | Bật ESP32 / chạy script giả lập | Serial Monitor hoặc terminal báo gửi dữ liệu thành công |
| 2 | Hà hơi / che DHT22 | Độ ẩm tăng, hiển thị trên trang giám sát |
| 3 | Bấm bật quạt trên web | Relay kênh 2 đóng trong vòng 3 giây |
| 4 | Độ ẩm thấp hơn ngưỡng | Server trả lệnh bật phun sương, relay kênh 1 đóng |
| 5 | Tắt backend hơn 2 phút | ESP32 tự tắt toàn bộ relay |
| 6 | Tắt rồi bật lại Wi-Fi | ESP32 tự kết nối lại, tiếp tục gửi dữ liệu |

## 7. Lưu ý tích hợp

Firmware gọi 2 API `POST /api/iot/readings` và `GET /api/iot/commands`, xác thực bằng header `x-device-key`. Backend cần có 2 API này (phần Backend IoT) thì ESP32 mới gửi được dữ liệu.
