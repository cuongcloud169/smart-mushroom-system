// Gọi API backend. Đổi địa chỉ trong file .env (VITE_API_URL) nếu backend chạy ở máy/cổng khác.
export const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

// Gọi API, tự gắn token đăng nhập; lỗi -> ném Error với thông báo của server
export async function api(path, method = 'GET', body) {
  const token = localStorage.getItem('token');
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401 && token) {
    localStorage.removeItem('token');
    window.location.reload(); // token hết hạn -> về màn hình đăng nhập
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(data?.error || 'Lỗi ' + res.status);
  return data;
}

// Tải file CSV từ API báo cáo
export async function download(path, filename) {
  const res = await fetch(API + path, { headers: { Authorization: 'Bearer ' + localStorage.getItem('token') } });
  if (!res.ok) throw new Error('Không tải được file');
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

// Định dạng hiển thị
export const fmtDate = (v) => (v ? String(v).slice(0, 10) : '');
export const fmtTime = (v) => (v ? String(v).slice(0, 16).replace('T', ' ') : '');
export const fmtMoney = (v) => (v == null ? '' : Number(v).toLocaleString('vi-VN') + ' đ');

export const LABELS = {
  tray: { empty: 'Trống', rented: 'Đã thuê', growing: 'Đang trồng', ready_harvest: 'Sẵn sàng thu hoạch', harvested: 'Đã thu hoạch', maintenance: 'Bảo trì' },
  room: { active: 'Hoạt động', maintenance: 'Bảo trì', inactive: 'Ngừng' },
  rental: { pending: 'Chờ duyệt', active: 'Đang thuê', completed: 'Hoàn tất', cancelled: 'Đã hủy' },
  delivery: { pending: 'Chờ xác nhận', confirmed: 'Đã xác nhận', delivering: 'Đang giao', done: 'Hoàn tất', cancelled: 'Đã hủy' },
  harvest: { planned: 'Đã lên lịch', harvested: 'Đã thu hoạch', cancelled: 'Đã hủy' },
  role: { admin: 'Quản trị viên', operator: 'Người vận hành', customer: 'Khách hàng' },
  deviceType: { controller: 'Bộ điều khiển ESP32', sensor: 'Cảm biến', camera: 'Camera', actuator: 'Thiết bị điều khiển' },
  actuator: { mist: 'Phun sương', fan: 'Quạt thông gió', light: 'Đèn LED', heater: 'Sưởi' },
  tx: { pending: 'Chờ thanh toán', paid: 'Đã thanh toán', refunded: 'Đã hoàn tiền', failed: 'Không thành công' },
};

// Chuyển object nhãn thành danh sách lựa chọn cho ô select
export const options = (labels) => Object.entries(labels).map(([value, label]) => ({ value, label }));
