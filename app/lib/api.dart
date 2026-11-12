import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Địa chỉ backend Spring Boot.
/// - Máy ảo Android: 10.0.2.2 trỏ về máy tính đang chạy backend.
/// - Điện thoại thật: dùng IP LAN của máy chạy backend, vd http://192.168.1.10:8080/api
/// Có thể đổi khi chạy: flutter run --dart-define=API_URL=http://192.168.1.10:8080/api
const String baseUrl = String.fromEnvironment('API_URL', defaultValue: 'http://10.0.2.2:8080/api');

/// Lỗi trả về từ server (thông báo tiếng Việt trong trường "error")
class ApiError implements Exception {
  final int status;
  final String message;
  ApiError(this.status, this.message);
  @override
  String toString() => message;
}

class Api {
  static String? token;

  /// Đọc token đã lưu khi mở app
  static Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    token = p.getString('token');
  }

  static Future<void> saveToken(String? t) async {
    token = t;
    final p = await SharedPreferences.getInstance();
    if (t == null) {
      await p.remove('token');
    } else {
      await p.setString('token', t);
    }
  }

  /// Hàm chung: gọi API, tự gắn token, ném ApiError kèm thông báo của server
  static Future<dynamic> call(String method, String path, [Map<String, dynamic>? body]) async {
    final uri = Uri.parse(baseUrl + path);
    final headers = {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
    final encoded = jsonEncode(body ?? {});
    late http.Response res;
    try {
      switch (method) {
        case 'GET':
          res = await http.get(uri, headers: headers).timeout(const Duration(seconds: 15));
          break;
        case 'PUT':
          res = await http.put(uri, headers: headers, body: encoded).timeout(const Duration(seconds: 15));
          break;
        case 'DELETE':
          res = await http.delete(uri, headers: headers).timeout(const Duration(seconds: 15));
          break;
        default:
          res = await http.post(uri, headers: headers, body: encoded).timeout(const Duration(seconds: 15));
      }
    } catch (_) {
      throw ApiError(0, 'Không kết nối được máy chủ. Kiểm tra mạng và địa chỉ server.');
    }
    final data = res.bodyBytes.isEmpty ? null : jsonDecode(utf8.decode(res.bodyBytes));
    if (res.statusCode >= 400) {
      final msg = data is Map && data['error'] != null ? data['error'].toString() : 'Lỗi ${res.statusCode}';
      throw ApiError(res.statusCode, msg);
    }
    return data;
  }

  static Future<dynamic> get(String path) => call('GET', path);
  static Future<dynamic> post(String path, [Map<String, dynamic>? body]) => call('POST', path, body);
  static Future<dynamic> put(String path, [Map<String, dynamic>? body]) => call('PUT', path, body);
}

/// Định dạng hiển thị
String fmtDate(dynamic v) => v == null ? '' : v.toString().substring(0, v.toString().length >= 10 ? 10 : v.toString().length);
String fmtTime(dynamic v) {
  if (v == null) return '';
  final s = v.toString().replaceFirst('T', ' ');
  return s.length >= 16 ? s.substring(0, 16) : s;
}

String fmtMoney(dynamic v) {
  if (v == null) return '';
  final n = num.tryParse(v.toString())?.round() ?? 0;
  final s = n.toString().replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (m) => '.');
  return '$s đ';
}

const trayLabels = {
  'empty': 'Trống', 'rented': 'Đã thuê', 'growing': 'Đang trồng',
  'ready_harvest': 'Sẵn sàng thu hoạch', 'harvested': 'Đã thu hoạch', 'maintenance': 'Bảo trì',
};
const rentalLabels = {'pending': 'Chờ duyệt', 'active': 'Đang thuê', 'completed': 'Hoàn tất', 'cancelled': 'Đã hủy'};
const deliveryLabels = {
  'pending': 'Chờ xác nhận', 'confirmed': 'Đã xác nhận', 'delivering': 'Đang giao', 'done': 'Hoàn tất', 'cancelled': 'Đã hủy',
};
const txLabels = {'pending': 'Chờ thanh toán', 'paid': 'Đã thanh toán', 'refunded': 'Đã hoàn tiền', 'failed': 'Không thành công'};
