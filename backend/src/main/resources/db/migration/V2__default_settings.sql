-- Ngưỡng mặc định toàn hệ thống (room_id NULL)
INSERT INTO thresholds (room_id) VALUES (NULL);

-- Cài đặt thông báo mặc định
INSERT INTO notification_settings (event_type, label, enabled, notify_staff, notify_customer) VALUES
  ('env_alert',     'Cảnh báo môi trường vượt ngưỡng', TRUE, TRUE,  TRUE),
  ('device_offline','Thiết bị mất kết nối',            TRUE, TRUE,  FALSE),
  ('progress',      'Cập nhật tiến độ khay',            TRUE, FALSE, TRUE),
  ('harvest',       'Lịch thu hoạch / sẵn sàng thu hoạch', TRUE, FALSE, TRUE),
  ('rental',        'Đơn thuê mới / duyệt đơn thuê',    TRUE, TRUE,  TRUE),
  ('rental_expiry', 'Hợp đồng sắp hết hạn / đã hết hạn', TRUE, TRUE, TRUE),
  ('delivery',      'Yêu cầu nhận / giao nấm',          TRUE, TRUE,  TRUE);
