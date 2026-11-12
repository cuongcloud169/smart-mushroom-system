"""Kiểm thử end-to-end API backend. Cách chạy (backend + PostgreSQL đang chạy, CSDL mới tạo):
    python tests/e2e_test.py <API_KEY_ESP32>
API key in ra log khi backend khởi động lần đầu."""
import json, sys, urllib.request, urllib.error, subprocess

BASE = 'http://localhost:8080/api'
DEVICE_KEY = sys.argv[1]
results = []

def call(method, path, body=None, token=None, headers=None, raw=False):
    h = {'Content-Type': 'application/json'}
    if token: h['Authorization'] = 'Bearer ' + token
    if headers: h.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req) as r:
            txt = r.read()
            return r.status, (txt if raw else (json.loads(txt) if txt else None))
    except urllib.error.HTTPError as e:
        txt = e.read()
        try: return e.code, json.loads(txt)
        except Exception: return e.code, txt

def check(name, cond, info=''):
    results.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else f'  -> {info}'))

def sql(q):
    return subprocess.run(['psql', '-h', 'localhost', '-U', 'mushroom', '-d', 'mushroom', '-tAc', q],
                          env={'PGPASSWORD': 'mushroom', 'PATH': '/usr/bin:/bin'}, capture_output=True, text=True).stdout.strip()

# ---------- Xác thực ----------
s, r = call('POST', '/auth/login', {'email': 'admin@mushroom.local', 'password': 'Admin@123'}); A = r['token']
check('Đăng nhập admin', s == 200 and r['user']['role'] == 'admin' and 'password_hash' not in r['user'], r)
s, r = call('POST', '/auth/login', {'email': 'operator@mushroom.local', 'password': 'Operator@123'}); O = r['token']
s, r = call('POST', '/auth/login', {'email': 'customer@mushroom.local', 'password': 'Customer@123'}); C = r['token']; CID = r['user']['id']
check('Đăng nhập khách', s == 200 and r['user']['full_name'] == 'Khách hàng mẫu', r)
s, r = call('POST', '/auth/login', {'email': 'admin@mushroom.local', 'password': 'sai'})
check('Sai mật khẩu -> 401', s == 401 and r['error'] == 'Sai email hoặc mật khẩu', r)
s, r = call('GET', '/rooms')
check('Không token -> 401 JSON', s == 401 and 'error' in r, r)
s, r = call('POST', '/auth/register', {'email': 'khach2@test.vn', 'password': '12345678', 'full_name': 'Khách 2', 'phone': '0912345678'})
check('Khách tự đăng ký', s == 201 and r['user']['role'] == 'customer', r); C2 = r['token']
s, r = call('POST', '/auth/register', {'email': 'khach2@test.vn', 'password': '12345678', 'full_name': 'X'})
check('Email trùng -> 409', s == 409, r)
s, r = call('POST', '/auth/register', {'email': 'bad', 'password': '123', 'full_name': 'X'})
check('Email/mật khẩu sai định dạng -> 400', s == 400, r)
s, r = call('POST', '/auth/register', {'email': 'p@test.vn', 'password': '12345678', 'full_name': 'X', 'phone': 'abc'})
check('SĐT sai -> 400', s == 400, r)
s, r = call('POST', '/auth/register-admin', {'email': 'ad2@test.vn', 'password': '12345678', 'full_name': 'Admin 2', 'invite_code': 'sai'})
check('Đăng ký admin sai mã mời -> 403', s == 403, r)
s, r = call('POST', '/auth/register-admin', {'email': 'ad2@test.vn', 'password': '12345678', 'full_name': 'Admin 2', 'invite_code': 'MUSHROOM-ADMIN-2026'})
check('Đăng ký admin đúng mã mời', s == 201 and r['user']['role'] == 'admin', r)
s, r = call('PUT', '/auth/me', {'full_name': 'Khách Mẫu', 'phone': '0987654321', 'address': 'Q1'}, C)
check('Khách sửa hồ sơ', s == 200 and r['phone'] == '0987654321', r)
s, r = call('PUT', '/auth/me/password', {'old_password': 'Customer@123', 'new_password': 'Customer@456'}, C)
s2, _ = call('POST', '/auth/login', {'email': 'customer@mushroom.local', 'password': 'Customer@456'})
check('Đổi mật khẩu', s == 200 and s2 == 200, r)

# ---------- Người dùng (Admin) ----------
s, r = call('GET', '/users', token=O)
check('Operator xem users -> 403', s == 403, r)
s, r = call('POST', '/users', {'email': 'op2@test.vn', 'password': '12345678', 'full_name': 'Op 2', 'role': 'operator'}, A)
check('Admin tạo tài khoản', s == 201, r); U2 = r['id']
s, r = call('PUT', f'/users/{U2}', {'role': 'customer', 'is_active': False}, A)
check('Admin đổi vai trò + vô hiệu hóa', s == 200 and r['role'] == 'customer' and r['is_active'] is False, r)
s, r = call('POST', '/auth/login', {'email': 'op2@test.vn', 'password': '12345678'})
check('Tài khoản bị vô hiệu không đăng nhập được', s == 403, r)
s, r = call('POST', f'/users/{U2}/reset-password', {}, A)
check('Đặt lại mật khẩu', s == 200 and len(r['new_password']) >= 8, r)
s, r = call('DELETE', f'/users/{U2}', token=A)
check('Xóa tài khoản', s == 200, r)
s, r = call('GET', '/users/customers', token=O)
check('Operator xem danh sách khách', s == 200 and len(r) >= 2, r)

# ---------- Phòng, khay, gói thuê ----------
s, r = call('POST', '/rooms', {'name': 'Phòng B1', 'capacity': 5, 'status': 'active'}, O)
check('Operator thêm phòng', s == 201 and r['id'], r); RB = r['id']
s, r = call('PUT', f'/rooms/{RB}', {'description': 'Nấm linh chi'}, O)
check('Sửa phòng (giữ nguyên trường khác)', s == 200 and r['description'] == 'Nấm linh chi' and r['name'] == 'Phòng B1', r)
s, r = call('POST', '/rooms', {'name': 'Phòng B1'}, O)
check('Tên phòng trùng -> 409', s == 409, r)
s, r = call('POST', '/rooms', {'name': ''}, O)
check('Tên phòng trống -> 400', s == 400, r)
s, r = call('POST', '/trays', {'room_id': RB, 'mushroom_type': 'Linh chi', 'status': 'empty'}, O)
check('Thêm khay, tự sinh mã', s == 201 and r['code'] == f'TRAY-{RB}-001', r); TB = r['id']
s, r = call('DELETE', f'/rooms/{RB}', token=O)
check('Operator xóa phòng -> 403', s == 403, r)
s, r = call('DELETE', f'/rooms/{RB}', token=A)
check('Xóa phòng còn khay -> 409', s == 409, r)
s, r = call('GET', '/packages', token=C)
check('Khách xem gói thuê', s == 200 and len(r) == 2, r); PKG = r[0]['id']
s, r = call('POST', '/packages', {'name': 'Gói thử', 'duration_days': 7, 'price': 50000}, O)
check('Operator thêm gói -> 403', s == 403, r)
s, r = call('POST', '/packages', {'name': 'Gói 7 ngày', 'duration_days': 7, 'price': 50000, 'is_active': False}, A)
check('Admin thêm gói', s == 201, r)
s, r = call('GET', '/packages', token=C)
check('Khách không thấy gói ngừng bán', len(r) == 2, r)

# ---------- IoT ----------
DK = {'x-device-key': DEVICE_KEY}
s, r = call('POST', '/iot/readings', {'temperature': 25, 'humidity': 88, 'co2': 700}, headers=DK)
check('ESP32 gửi dữ liệu', s == 201 and r['ok'] and len(r['commands']) == 4 and r['alerts'] == [], r)
s, r = call('POST', '/iot/readings', {'temperature': 25}, headers={'x-device-key': 'sai'})
check('Sai API key -> 401', s == 401, r)
s, r = call('POST', '/iot/readings', {'temperature': -999}, headers=DK)
check('Số đo bất thường -> 400', s == 400, r)
s, r = call('GET', '/iot/commands', headers=DK)
check('ESP32 hỏi lệnh', s == 200 and len(r['commands']) == 4, r)
s, th = call('GET', '/thresholds', token=O)
default_id = [t for t in th if t['room_id'] is None][0]['id']
s, r = call('PUT', f'/thresholds/{default_id}', {'temp_max': 22}, O)
check('Operator sửa ngưỡng -> 403', s == 403, r)
s, r = call('PUT', f'/thresholds/{default_id}', {'temp_min': 30, 'temp_max': 22}, A)
check('Ngưỡng min >= max -> 400', s == 400, r)
s, r = call('PUT', f'/thresholds/{default_id}', {'temp_max': 22, 'hum_min': 90}, A)
check('Admin sửa ngưỡng', s == 200 and float(r['temp_max']) == 22, r)
s, r = call('POST', '/iot/readings', {'temperature': 25, 'humidity': 85, 'co2': 1500}, headers=DK)
kinds = {c['kind']: c['state'] for c in r['commands']}
check('Vượt ngưỡng -> tạo cảnh báo', s == 201 and set(r['alerts']) == {'temp_high', 'hum_low', 'co2_high'}, r)
check('Tự động: bật quạt + phun sương', kinds['fan'] is True and kinds['mist'] is True, kinds)
s, r = call('POST', '/iot/readings', {'temperature': 25, 'humidity': 85, 'co2': 1500}, headers=DK)
check('Không lặp cảnh báo trong 10 phút', r['alerts'] == [], r)
s, st = call('GET', '/iot/devices/status', token=O)
fan = [d for d in st if d['actuator_kind'] == 'fan'][0]
esp = [d for d in st if d['type'] == 'controller'][0]
check('ESP32 hiện online', esp['online'] is True, esp)
s, r = call('POST', f'/iot/devices/{fan["id"]}/control', {'state': False}, O)
check('Tắt quạt thủ công -> chế độ manual', s == 200 and r['state'] is False and r['mode'] == 'manual', r)
s, r = call('POST', '/iot/readings', {'temperature': 25, 'humidity': 85, 'co2': 1500}, headers=DK)
check('Thiết bị manual không bị tự động bật lại', {c['kind']: c['state'] for c in r['commands']}['fan'] is False, r)
s, r = call('POST', f'/iot/devices/{fan["id"]}/control', {'mode': 'auto'}, O)
check('Chuyển về tự động', r['mode'] == 'auto', r)
s, r = call('GET', '/iot/rooms/1/latest', token=O)
check('Số đo mới nhất', s == 200 and float(r['co2']) == 1500, r)
s, r = call('GET', '/iot/rooms/1/history?hours=24', token=O)
check('Lịch sử theo giờ (biểu đồ)', s == 200 and len(r) >= 1 and 'temperature' in r[0], r)
s, r = call('GET', '/iot/rooms/1/readings?limit=5', token=O)
check('Danh sách số đo', s == 200 and len(r) == 5, r)
s, r = call('GET', '/iot/alerts?resolved=false', token=O)
check('Danh sách cảnh báo', s == 200 and len(r) == 3, r); AL = r[0]['id']
s, r = call('PUT', f'/iot/alerts/{AL}/resolve', token=O)
check('Xử lý cảnh báo', s == 200 and r['resolved'] is True, r)
s, r = call('GET', '/iot/alerts', token=C)
check('Khách xem cảnh báo hệ thống -> 403', s == 403, r)
call('PUT', f'/thresholds/{default_id}', {'temp_max': 28, 'hum_min': 80}, A)

# ---------- Thiết bị & camera ----------
s, r = call('POST', '/devices', {'room_id': RB, 'name': 'Camera B1', 'type': 'camera', 'stream_url': 'http://cam/b1.jpg'}, O)
check('Operator thêm camera', s == 201 and 'api_key' not in r, r); CAM = r['id']
s, r = call('POST', '/devices', {'room_id': RB, 'name': 'Relay', 'type': 'actuator'}, O)
check('Thiết bị điều khiển thiếu loại -> 400', s == 400, r)
s, r = call('PUT', f'/devices/{CAM}', {'stream_url': 'http://cam/b1-new.jpg'}, O)
check('Operator sửa URL camera', s == 200 and r['stream_url'].endswith('new.jpg'), r)
s, r = call('GET', f'/devices/{esp["id"]}/api-key', token=O)
check('Operator xem API key -> 403', s == 403, r)
s, r = call('POST', '/devices', {'room_id': RB, 'name': 'ESP32 B1', 'type': 'controller'}, A)
s, r = call('POST', f'/devices/{r["id"]}/regenerate-key', token=A)
check('Admin cấp lại API key', s == 200 and len(r['api_key']) == 32, r)

# ---------- Thuê khay: đơn chờ duyệt ----------
s, avail = call('GET', '/trays/available', token=C)
check('Khách xem khay trống', s == 200 and len(avail) == 7, len(avail))
t1, t2 = avail[0]['id'], avail[1]['id']
s, r = call('POST', '/rentals', {'tray_ids': [t1, t2], 'package_id': PKG}, C)
check('Khách thuê 2 khay -> chờ duyệt', s == 201 and len(r) == 2 and r[0]['status'] == 'pending', r)
R1, R2 = r[0]['id'], r[1]['id']
s, r = call('POST', '/rentals', {'tray_ids': [t1], 'package_id': PKG}, C2)
check('Thuê khay đã có người -> 409', s == 409, r)
s, r = call('POST', '/rentals', {'tray_ids': [avail[2]['id'], t1], 'package_id': PKG}, C2)
s2, av2 = call('GET', '/trays/available', token=C2)
check('Lỗi 1 khay -> hủy cả đơn (transaction)', s == 409 and len(av2) == 5, (s, len(av2)))
s, r = call('GET', '/trays/mine', token=C)
check('Khay của tôi hiện đơn chờ duyệt', len(r) == 2 and r[0]['rental_status'] == 'pending', r)
s, r = call('GET', f'/trays/{t1}/detail', token=C)
check('Chưa duyệt -> chưa xem chi tiết khay', s == 403, r)
s, r = call('PUT', f'/rentals/{R1}/approve', token=C)
check('Khách tự duyệt -> 403', s == 403, r)
s, r = call('PUT', f'/rentals/{R1}/approve', token=O)
check('Operator duyệt đơn', s == 200 and r['status'] == 'active', r)
s, tx = call('GET', '/rentals/transactions', token=C)
check('Giao dịch chuyển đã thanh toán', any(t['rental_id'] == R1 and t['status'] == 'paid' for t in tx), tx)
s, r = call('PUT', f'/rentals/{R2}/status', {'status': 'cancelled'}, C)
s2, tx = call('GET', '/rentals/transactions', token=C)
check('Khách hủy đơn chờ -> giao dịch failed', s == 200 and any(t['rental_id'] == R2 and t['status'] == 'failed' for t in tx), tx)
s, r = call('PUT', f'/rentals/{R1}/status', {'status': 'completed'}, C)
check('Khách tự hoàn tất -> 403', s == 403, r)
s, r = call('GET', f'/trays/{t1}/detail', token=C)
check('Khách xem chi tiết khay đang thuê', s == 200 and 'progress_logs' in r and 'harvests' in r, r)
s, r = call('GET', f'/trays/{t1}/detail', token=C2)
check('Khách khác xem khay -> 403', s == 403, r)
s, r = call('GET', f'/trays/{t1}/readings?limit=10', token=C)
check('Khách xem môi trường khay', s == 200 and len(r) >= 1, r)
s, r = call('GET', f'/trays/{t1}/camera', token=C)
check('Khách xem camera khay', s == 200 and len(r) == 1, r)

# ---------- Bàn giao + nhập mã khay ----------
t3 = avail[3]
s, r = call('POST', '/rentals/assign', {'tray_id': t3['id'], 'package_id': PKG}, O)
check('Operator bàn giao khay chưa có khách', s == 201 and r['customer_id'] is None and r['status'] == 'active', r)
s, r = call('POST', '/rentals/claim', {'code': t3['code'].lower()}, C2)
check('Khách nhập mã khay (không phân biệt hoa thường)', s == 200 and r['customer_id'] is not None, r)
s, r = call('POST', '/rentals/claim', {'code': t3['code']}, C)
check('Nhập mã khay đã có chủ -> 404', s == 404, r)
s, r = call('PUT', f'/rentals/{r.get("id", 0) or 0}/status', {'status': 'cancelled'}, O)

# ---------- Tiến độ + thông báo ----------
s, r = call('PUT', f'/trays/{t1}/progress', {'growth_stage': 'Ủ tơ', 'status': 'growing', 'fill_percent': 40, 'note': 'Tơ nấm phát triển tốt'}, O)
check('Operator cập nhật tiến độ', s == 200 and r['status'] == 'growing' and r['fill_percent'] == 40, r)
s, r = call('PUT', f'/trays/{t1}/progress', {'status': 'xyz'}, O)
check('Trạng thái khay sai -> 400', s == 400, r)
s, r = call('GET', '/notifications', token=C)
titles = [n['title'] for n in r]
check('Khách nhận thông báo tiến độ + duyệt đơn + cảnh báo',
      any('tiến độ' in t for t in titles) and any('duyệt' in t for t in titles), titles)
s, r = call('GET', '/notifications/unread-count', token=C)
check('Đếm thông báo chưa đọc', s == 200 and r['count'] >= 2, r)
s, r = call('PUT', '/notifications/read-all', token=C)
s, r = call('GET', '/notifications/unread-count', token=C)
check('Đọc tất cả', r['count'] == 0, r)
s, r = call('PUT', '/notification-settings/progress', {'enabled': False}, O)
check('Operator sửa cài đặt thông báo -> 403', s == 403, r)
s, r = call('PUT', '/notification-settings/progress', {'enabled': False}, A)
check('Admin tắt thông báo tiến độ', s == 200 and r['enabled'] is False, r)
call('PUT', f'/trays/{t1}/progress', {'growth_stage': 'Ra quả thể'}, O)
s, r = call('GET', '/notifications/unread-count', token=C)
check('Đã tắt thì không gửi thông báo tiến độ', r['count'] == 0, r)
call('PUT', '/notification-settings/progress', {'enabled': True}, A)

# ---------- Thu hoạch ----------
s, r = call('POST', '/harvests', {'tray_id': t1, 'planned_date': '2026-12-01', 'note': 'Đợt 1'}, O)
check('Lên lịch thu hoạch', s == 201 and str(r['planned_date']).startswith('2026-12-01'), r); H = r['id']
s, r = call('GET', '/harvests', token=C)
check('Khách xem lịch thu hoạch khay mình', s == 200 and len(r) == 1, r)
s, r = call('PUT', f'/harvests/{H}', {'status': 'harvested'}, O)
check('Ghi nhận thu hoạch thiếu sản lượng -> 400', s == 400, r)
s, r = call('PUT', f'/harvests/{H}', {'status': 'harvested', 'yield_kg': 2.5}, O)
check('Ghi nhận thu hoạch', s == 200 and float(r['yield_kg']) == 2.5 and r['harvested_at'], r)
s, r = call('GET', f'/trays/{t1}', token=O)
check('Khay chuyển sang "harvested"', r['status'] == 'harvested', r)

# ---------- Nhận / giao nấm ----------
s, r = call('POST', '/deliveries', {'tray_id': t1, 'type': 'delivery'}, C)
check('Giao tận nơi thiếu địa chỉ -> 400', s == 400, r)
s, r = call('POST', '/deliveries', {'tray_id': t1, 'type': 'delivery', 'address': '1 Lê Lợi', 'note': 'Chiều thứ 7'}, C)
check('Khách yêu cầu giao nấm', s == 201 and r['status'] == 'pending', r); D = r['id']
s, r = call('POST', '/deliveries', {'tray_id': t1, 'type': 'pickup'}, C2)
check('Yêu cầu cho khay không thuê -> 403', s == 403, r)
s, r = call('PUT', f'/deliveries/{D}/status', {'status': 'delivering'}, O)
check('Operator cập nhật giao hàng', s == 200 and r['status'] == 'delivering', r)
s, r = call('PUT', f'/deliveries/{D}/status', {'status': 'cancelled'}, C)
check('Khách hủy khi đã giao -> 404', s == 404, r)

# ---------- Báo cáo ----------
s, r = call('GET', '/reports/summary', token=O)
check('Dashboard tổng quan', s == 200 and r['rooms'] == 2 and r['devices']['online'] == 1 and float(r['revenue_total']) > 0, r)
s, r = call('GET', '/reports/environment?days=7', token=O)
check('Báo cáo môi trường', s == 200 and len(r) == 1, r)
s, r = call('GET', '/reports/rentals', token=O)
check('Báo cáo cho thuê', s == 200 and r[0]['rentals'] >= 3, r)
s, r = call('GET', '/reports/harvests?format=csv', token=O, raw=True)
check('Xuất CSV thu hoạch (BOM UTF-8)', s == 200 and r.startswith('﻿'.encode()), r[:50])
s, r = call('GET', '/reports/export/users', token=A, raw=True)
check('Admin xuất dữ liệu thô, không lộ mật khẩu', s == 200 and b'password' not in r, r[:80])
s, r = call('GET', '/reports/export/pg_shadow', token=A)
check('Xuất bảng không cho phép -> 400', s == 400, r)

# ---------- Job hết hạn hợp đồng (gọi trực tiếp bằng SQL giống job) ----------
s, mine = call('GET', '/trays/mine', token=C)
active = [m for m in mine if m['rental_status'] == 'active'][0]
sql(f"UPDATE rentals SET end_date = CURRENT_DATE - 1 WHERE id = {active['rental_id']}")

# ---------- Swagger ----------
s, r = call('GET', '/../v3/api-docs')
check('Swagger OpenAPI', s == 200 and len(r['paths']) > 40, s)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} kiểm tra đạt")
print('RENTAL_TO_EXPIRE', active['rental_id'], active['id'])
