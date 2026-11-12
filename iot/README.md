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
