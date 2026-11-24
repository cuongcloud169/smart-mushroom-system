# Build app khách hàng (Flutter) cho điện thoại Android

Thư mục `app/` chỉ chứa mã nguồn (`lib/`) và `pubspec.yaml`. Để chạy trên Android cần tạo khung project Flutter rồi chép mã nguồn vào.

## 1. Cài đặt

- Flutter SDK (ví dụ `git clone https://github.com/flutter/flutter.git -b stable C:\src\flutter`, thêm `C:\src\flutter\bin` vào PATH).
- Android Studio (Android SDK). Nếu build báo thiếu NDK: SDK Manager → SDK Tools → *Show Package Details* → NDK đúng phiên bản được báo.
- Windows: bật **Developer Mode** (`start ms-settings:developers`) để Flutter build được plugin.
- Kiểm tra: `flutter doctor` – cần dấu ✓ ở **Flutter** và **Android toolchain**.

## 2. Tạo project và chép mã nguồn

```powershell
cd C:\Users\<ten>
flutter create mushroom_app
cd mushroom_app
Copy-Item -Recurse -Force <đường-dẫn-repo>\app\lib .\
Copy-Item -Force <đường-dẫn-repo>\app\pubspec.yaml .\
Remove-Item -Recurse -Force test
Remove-Item analysis_options.yaml
flutter pub get
```

## 3. Cho phép gọi HTTP

Sửa `android\app\src\main\AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <application
        android:usesCleartextTraffic="true"
        android:label="mushroom_app"
        ...
```

Thiếu bước này app báo lỗi *Cleartext HTTP traffic not permitted*.

## 4. Build và cài

Backend phải đang chạy (`docker compose up -d`), điện thoại và laptop cùng mạng Wi-Fi. Lấy IP laptop bằng `ipconfig`.

```powershell
# cắm điện thoại (bật Gỡ lỗi USB)
flutter run -d <id-dien-thoai> --dart-define=API_URL=http://<IP-laptop>:8080/api

# hoặc build file APK để cài thủ công
flutter build apk --dart-define=API_URL=http://<IP-laptop>:8080/api
# file: build\app\outputs\flutter-apk\app-release.apk
```

Máy ảo Android: chỉ cần `flutter run`, app mặc định trỏ tới `10.0.2.2:8080`.

## 5. Lưu ý

- Kiểm tra kết nối: mở `http://<IP-laptop>:8080/api/health` bằng trình duyệt trên điện thoại.
- Không mở được: mở cổng trên Windows Firewall (PowerShell quyền Admin):
  `New-NetFirewallRule -DisplayName "Mushroom 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Any`
- IP laptop đổi (đổi mạng Wi-Fi) thì phải build lại APK với IP mới.
- Đăng nhập bằng tài khoản **khách hàng** (đăng ký trong app). Đơn thuê khay được duyệt trên web quản trị → **Cho thuê** → **Duyệt**.
