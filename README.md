# Smart Mushroom System

**Hệ thống quản lý nuôi trồng nấm thông minh tích hợp chức năng cho thuê và giám sát từ xa**

Đồ án môn **Lập trình Java**. Trang trại quản lý phòng trồng và khay nấm, cho khách hàng thuê khay để tự theo dõi nấm phát triển; thiết bị IoT (ESP32) đo nhiệt độ, độ ẩm, CO2 và tự động bật/tắt phun sương, quạt, đèn, sưởi theo ngưỡng.

---

## Mục lục

1. [Thành phần hệ thống](#1-thành-phần-hệ-thống)
2. [Kiến trúc](#2-kiến-trúc)
3. [Cấu trúc thư mục](#3-cấu-trúc-thư-mục)
4. [Chạy nhanh bằng Docker](#4-chạy-nhanh-bằng-docker)
5. [Chạy từng phần khi lập trình](#5-chạy-từng-phần-khi-lập-trình)
6. [Tài khoản mẫu](#6-tài-khoản-mẫu)
7. [Kịch bản kiểm tra](#7-kịch-bản-kiểm-tra)
8. [Chức năng theo vai trò và API](#8-chức-năng-theo-vai-trò-và-api)
9. [Kiểm thử](#9-kiểm-thử)
10. [Thành viên và phân công](#10-thành-viên-và-phân-công)
11. [Quy trình làm việc với Git](#11-quy-trình-làm-việc-với-git)
12. [Giới hạn hiện tại](#12-giới-hạn-hiện-tại)

---

## 1. Thành phần hệ thống

| Phần | Công nghệ | Thư mục |
|---|---|---|
| RESTful API + CSDL | **Java 21, Spring Boot**, Spring Security (JWT, BCrypt), Spring Data JPA + JdbcTemplate, PostgreSQL 16, Flyway, Swagger | `backend/` |
| Web quản trị (Admin, Farm Operator) | ReactJS 18 (Vite), Recharts | `web/` |
| App khách hàng | Flutter 3 | `app/` |
| IoT | ESP32 (Arduino), DHT22, MH-Z19B, relay 4 kênh | `iot/firmware/` |
| Giả lập ESP32 | Node.js | `tools/` |
| Kiểm thử end-to-end | Python | `tests/` |
| Triển khai | Docker, docker-compose | `docker-compose.yml`, `backend/Dockerfile`, `web/Dockerfile` |

---

## 2. Kiến trúc

```
┌──────────────┐  HTTP/JSON + JWT  ┌─────────────────────────────┐  JDBC  ┌──────────────┐
│  Web Admin   │ ────────────────▶ │    Backend Spring Boot      │ ─────▶ │  PostgreSQL  │
│  (ReactJS)   │ ◀──────────────── │ Controller → Service → JPA  │ ◀───── │   (Flyway)   │
└──────────────┘                   │                             │        └──────────────┘
┌──────────────┐  HTTP/JSON + JWT  │  Job định kỳ: hết hạn thuê, │
│  App khách   │ ────────────────▶ │  thiết bị mất kết nối       │
│  (Flutter)   │ ◀──────────────── │                             │
└──────────────┘                   └─────────────────────────────┘
                                         ▲               │
                      POST /iot/readings │               │ lệnh bật/tắt
                      (x-device-key)     │               ▼
                                   ┌─────────────────────────────┐
                                   │  ESP32 + cảm biến + relay   │
                                   └─────────────────────────────┘
```

Backend tổ chức theo các tầng:

| Package | Vai trò |
|---|---|
| `controller` | RESTful API, nhận request và trả JSON |
| `service` | Nghiệp vụ: thuê khay, IoT, quy tắc môi trường, thông báo |
| `repository` | Truy cập dữ liệu bằng Spring Data JPA |
| `entity` | Lớp ánh xạ bảng PostgreSQL |
| `security` | JWT, lấy người dùng hiện tại, kiểm tra vai trò |
| `config` | Spring Security, CORS, Swagger, dữ liệu mẫu |
| `common` | Xử lý lỗi dùng chung, truy vấn JdbcTemplate |

---

## 3. Cấu trúc thư mục

```
smart-mushroom-system/
├── backend/                       # API Java Spring Boot
│   ├── src/main/java/com/mushroom/
│   │   ├── controller/   # Room, Tray, Package, Rental, Harvest, Delivery,
│   │   │                 # Device, Iot, Threshold, Notification, Report...
│   │   ├── service/      # RentalService, IotService, EnvironmentRules, NotificationService
│   │   ├── repository/   # Spring Data JPA
│   │   ├── entity/       # Room, Tray, RentalPackage, Device, Threshold...
│   │   └── config/       # DataSeeder (dữ liệu mẫu)
│   ├── src/test/java/    # Unit test
│   └── Dockerfile
├── web/                           # Web quản trị ReactJS
│   └── src/pages/        # Dashboard, Rooms, Trays, Rentals, Monitor, Alerts, Devices,
│                         # Settings, Harvests, Deliveries, Reports, Users...
├── app/                           # App Flutter cho khách hàng
│   └── lib/pages/        # Đăng nhập, Thuê khay, Khay của tôi, Chi tiết khay, Thông báo...
├── iot/firmware/firmware.ino      # Firmware ESP32
├── tools/simulate-esp32.mjs       # Giả lập ESP32
├── tests/e2e_test.py              # Kiểm thử end-to-end toàn bộ API
└── docker-compose.yml             # Chạy PostgreSQL + backend + web
```

---

## 4. Chạy nhanh bằng Docker

Cần cài **Docker Desktop**. Tại thư mục gốc:

```bash
docker compose up -d --build
```

| Thành phần | Địa chỉ |
|---|---|
| Web quản trị | http://localhost:5173 |
| Kiểm tra API | http://localhost:8080/api/health |
| Tài liệu API (Swagger) | http://localhost:8080/swagger-ui.html |

Xem API key của ESP32 mẫu: `docker compose logs backend | grep "API key"`
Tắt hệ thống: `docker compose down`

---

## 5. Chạy từng phần khi lập trình

### 5.1. Cơ sở dữ liệu
`docker compose up -d db`, hoặc cài PostgreSQL 16 và tạo database `mushroom`, user/mật khẩu `mushroom`.

### 5.2. Backend (Java)
- **IntelliJ IDEA**: File → Open → chọn thư mục `backend` → chạy lớp `MushroomApplication`.
- Hoặc dòng lệnh: `cd backend && mvn spring-boot:run`

Lần chạy đầu, **Flyway** tự tạo bảng và `DataSeeder` tạo dữ liệu mẫu, in API key của ESP32 ra log.

| Biến môi trường | Ý nghĩa | Mặc định |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Kết nối PostgreSQL | `jdbc:postgresql://localhost:5432/mushroom`, `mushroom`, `mushroom` |
| `JWT_SECRET` | Khóa ký token (≥ 32 ký tự), **đổi khi triển khai thật** | chuỗi dev |
| `ADMIN_INVITE_CODE` | Mã mời đăng ký Quản trị viên | `MUSHROOM-ADMIN-2026` |
| `CORS_ORIGINS` | Địa chỉ web được phép gọi API | `http://localhost:5173` |

### 5.3. Web
```bash
cd web
npm install
npm run dev        # http://localhost:5173
```
Đổi địa chỉ backend: tạo `web/.env` với `VITE_API_URL=http://<ip>:8080/api`.

### 5.4. App Flutter
```bash
flutter create mushroom_app
# chép pubspec.yaml và thư mục lib/ trong app/ vào mushroom_app/
cd mushroom_app
flutter pub get
flutter run                                                      # máy ảo Android (10.0.2.2:8080)
flutter run --dart-define=API_URL=http://192.168.1.10:8080/api   # điện thoại thật: IP LAN của máy chạy backend
```
Android chặn HTTP thường: thêm `android:usesCleartextTraffic="true"` vào thẻ `<application>` trong `AndroidManifest.xml` (chỉ khi chạy thử).

### 5.5. ESP32
Arduino IDE → cài board **esp32 by Espressif** và thư viện *DHT sensor library*, *Adafruit Unified Sensor*, *ArduinoJson* → sửa Wi-Fi, địa chỉ server, API key ở đầu `iot/firmware/firmware.ino` → chọn **ESP32 Dev Module** → Upload.

| Thiết bị | Chân ESP32 |
|---|---|
| DHT22 DATA | GPIO 4 |
| MH-Z19B TX / RX | GPIO 16 / GPIO 17 |
| Relay phun sương / quạt / đèn / sưởi | GPIO 25 / 26 / 27 / 14 |

Chưa có phần cứng thì dùng giả lập:
```bash
node tools/simulate-esp32.mjs <API_KEY> 5 http://localhost:8080
```

---

## 6. Tài khoản mẫu

| Vai trò | Email | Mật khẩu | Dùng ở |
|---|---|---|---|
| Administrator | admin@mushroom.local | Admin@123 | Web |
| Farm Operator | operator@mushroom.local | Operator@123 | Web |
| Customer | customer@mushroom.local | Customer@123 | App |

---

## 7. Kịch bản kiểm tra

1. Mở `/api/health` thấy `{"ok":true,...}`.
2. Chạy giả lập ESP32, thấy log `-> 201` kèm danh sách lệnh.
3. Web (admin) > **Giám sát & điều khiển**: số liệu và biểu đồ cập nhật; bấm Bật/Tắt thiết bị, log giả lập đổi lệnh.
4. App (customer) > **Thuê khay**: chọn gói + khay > gửi đơn. Web > **Cho thuê** > **Duyệt**. App nhận thông báo, khay hiện trong "Khay của tôi".
5. Web > **Cho thuê** > **Bàn giao khay** (bỏ trống khách). App > **Nhập mã khay** để nhận khay.
6. Web > **Khay trồng** > **Cập nhật tiến độ**; app nhận thông báo, xem nhật ký tiến độ.
7. Web > **Ngưỡng & thông báo**: hạ ngưỡng nhiệt độ tối đa, chờ giả lập gửi số đo, thấy cảnh báo + quạt tự bật.
8. Web > **Thu hoạch** > lên lịch, ghi nhận sản lượng; app gửi yêu cầu nhận/giao, web xử lý.
9. Web > **Báo cáo**: biểu đồ và xuất CSV.

---

## 8. Chức năng theo vai trò và API

| Vai trò | User story | Web / App | API chính |
|---|---|---|---|
| Admin | Đăng ký tài khoản admin | Web: Đăng nhập > "Đăng ký tài khoản Quản trị viên" | `POST /api/auth/register-admin` (mã mời) |
| Admin | Đăng nhập an toàn | Web | `POST /api/auth/login` (BCrypt + JWT) |
| Admin | Quản lý tài khoản, phân quyền, đặt lại mật khẩu | Web: Người dùng | `/api/users` |
| Admin | Quản lý phòng | Web: Phòng nuôi | `/api/rooms` |
| Admin | Quản lý khay, trạng thái, phân bổ | Web: Khay trồng, Cho thuê | `/api/trays`, `/api/rentals/assign` |
| Admin | Gói thuê và giá | Web: Gói thuê & giá | `/api/packages` |
| Admin | Thiết bị IoT, cảm biến, camera | Web: Thiết bị & camera | `/api/devices` |
| Admin | Thống kê, báo cáo | Web: Tổng quan, Báo cáo | `/api/reports/*` |
| Admin | Xuất dữ liệu | Web: Báo cáo > Xuất CSV | `?format=csv`, `/api/reports/export/{table}` |
| Admin | Cài đặt thông báo, ngưỡng môi trường | Web: Ngưỡng & thông báo | `/api/thresholds`, `/api/notification-settings` |
| Operator | Quản lý phòng, khay | Web | như trên |
| Operator | Gán khay cho khách | Web: Cho thuê > Bàn giao khay, Duyệt đơn | `/api/rentals/assign`, `/{id}/approve` |
| Operator | Giám sát nhiệt độ, độ ẩm, CO₂ | Web: Giám sát | `/api/iot/rooms/{id}/latest`, `/history` |
| Operator | Điều khiển phun sương, quạt, sưởi, đèn | Web: Giám sát (thủ công) + tự động theo ngưỡng | `/api/iot/devices/{id}/control` |
| Operator | Quản lý camera | Web: Thiết bị & camera | `/api/devices` |
| Operator | Cập nhật tiến độ, trạng thái khay | Web: Khay trồng | `PUT /api/trays/{id}/progress` |
| Operator | Lịch và kết quả thu hoạch | Web: Thu hoạch | `/api/harvests` |
| Operator | Nhận cảnh báo vượt ngưỡng | Web: Cảnh báo, Thông báo | `/api/iot/alerts`, `/api/notifications` |
| Operator | Lịch sử cảm biến, trạng thái thiết bị | Web: Giám sát, Thiết bị | `/api/iot/rooms/{id}/history`, `/devices/status` |
| Customer | Đăng ký, đăng nhập | App | `/api/auth/register`, `/login` |
| Customer | Xem/sửa hồ sơ | App: Tài khoản (+ đổi mật khẩu) | `/api/auth/me` |
| Customer | Xem khay trống và gói thuê | App: Thuê khay | `/api/trays/available`, `/api/packages` |
| Customer | Thuê 1 hoặc nhiều khay | App: Thuê khay | `POST /api/rentals` |
| Customer | Đăng ký khay bằng mã khay | App: Khay của tôi > Nhập mã khay | `POST /api/rentals/claim` |
| Customer | Chi tiết khay, giai đoạn, ngày thu hoạch | App: Chi tiết khay | `/api/trays/{id}/detail` |
| Customer | Xem nhiệt độ, độ ẩm, CO₂ | App: Chi tiết khay (+ biểu đồ) | `/api/trays/{id}/readings` |
| Customer | Xem camera | App: Chi tiết khay | `/api/trays/{id}/camera` |
| Customer | Nhận thông báo | App: Thông báo (có số chưa đọc) | `/api/notifications` |
| Customer | Lịch sử thuê và giao dịch | App: Tài khoản | `/api/rentals`, `/transactions` |
| Customer | Yêu cầu nhận/giao nấm | App: Chi tiết khay > Nhận/giao | `/api/deliveries` |

---

## 9. Kiểm thử

| Loại | Lệnh | Nội dung |
|---|---|---|
| Unit test | `cd backend && mvn test` | Quy tắc cảnh báo / tự động điều khiển (`EnvironmentRulesTest`), xuất CSV (`ReportCsvTest`) |
| End-to-end | `python tests/e2e_test.py` (backend đang chạy) | Gọi toàn bộ API: đăng nhập, phân quyền, thuê khay, IoT, cảnh báo, báo cáo |
| Thiết bị | `node tools/simulate-esp32.mjs <API_KEY>` | Giả lập ESP32 gửi dữ liệu và nhận lệnh |

---

## 10. Thành viên và phân công

| STT | Họ và tên | MSSV | Vai trò | Phụ trách |
|---|---|---|---|---|
| 1 | Huỳnh Quốc Cường | 052206000403 | Trưởng nhóm, Backend lõi | Khởi tạo dự án, CSDL + Flyway, JWT, đăng ký/đăng nhập, phân quyền, người dùng; duyệt Pull Request |
| 2 | Nguyễn Hoàng Phúc | 080207007955 | Backend IoT | Thiết bị, camera, dữ liệu cảm biến, ngưỡng, cảnh báo, tự động điều khiển, thông báo |
| 3 | Trần Cao Minh | 080207010772 | Backend nghiệp vụ | Phòng, khay, gói thuê, đơn thuê, mã khay, thu hoạch, giao nhận, báo cáo |
| 4 | Nguyễn Kim Phong | 038205012341 | Frontend Web | Khung web, đăng nhập, Dashboard, các trang quản lý, báo cáo |
| 5 | Nguyễn Bùi Dũng | 045205005999 | Web giám sát, Triển khai, Kiểm thử | Giám sát, điều khiển, cảnh báo, thiết bị, cấu hình ngưỡng; Docker; kiểm thử end-to-end |
| 6 | Trần Nguyễn Hải Duy | 083207009611 | Mobile | App Flutter cho khách hàng |
| 7 | Võ Duy Thịnh | 056206002399 | IoT | Firmware ESP32, giả lập ESP32, tài liệu IoT |

---

## 11. Quy trình làm việc với Git

- `main` chỉ nhận code qua **Pull Request**; mỗi phần việc làm trên nhánh riêng (`feature/KAN-xx-...`).
- Tin nhắn commit bắt đầu bằng mã task Jira, ví dụ `KAN-44: API quản lý phòng trồng nấm`.
- Merge bằng **Create a merge commit** (không Squash) để giữ lịch sử commit của từng thành viên.
- Không commit `target/`, `node_modules/`, `.env`, mật khẩu database hoặc mật khẩu Wi-Fi thật.

```bash
git checkout main && git pull
git checkout -b feature/KAN-xx-ten-chuc-nang
git add <file>
git commit -m "KAN-xx: Mô tả ngắn gọn"
git push origin feature/KAN-xx-ten-chuc-nang
```

---

## 12. Giới hạn hiện tại

- Thông báo hiển thị trong app (kiểm tra mỗi 30 giây), **chưa đẩy ra màn hình khóa** điện thoại. Muốn có cần thêm Firebase Cloud Messaging (tạo project Firebase).
- Camera hiển thị ảnh làm mới mỗi 2 giây (hợp với camera có URL snapshot/MJPEG); luồng RTSP/HLS cần thêm gói `video_player`.
- Thanh toán được ghi nhận khi nhân viên duyệt đơn (chưa tích hợp cổng thanh toán online).

---

Tài liệu chi tiết (phân tích yêu cầu, thiết kế, backend, IoT): **Confluence** của nhóm. Quản lý công việc: **Jira** project KAN.
