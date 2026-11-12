import React, { useState } from 'react';
import { api } from '../api.js';
import { act, FormModal, Table, useData } from '../components/ui.jsx';

const FIELDS = [
  { name: 'temp_min', label: 'Nhiệt độ tối thiểu (°C)', type: 'number', step: '0.1', required: true },
  { name: 'temp_max', label: 'Nhiệt độ tối đa (°C)', type: 'number', step: '0.1', required: true },
  { name: 'hum_min', label: 'Độ ẩm tối thiểu (%)', type: 'number', step: '0.1', required: true },
  { name: 'hum_max', label: 'Độ ẩm tối đa (%)', type: 'number', step: '0.1', required: true },
  { name: 'co2_max', label: 'CO₂ tối đa (ppm)', type: 'number', required: true },
  { name: 'light_on_hour', label: 'Giờ bật đèn LED (0-23)', type: 'number', required: true },
  { name: 'light_off_hour', label: 'Giờ tắt đèn LED (0-23)', type: 'number', required: true },
];

// Cấu hình ngưỡng môi trường + cài đặt thông báo (Admin sửa, Operator xem)
export default function Settings({ user }) {
  const [thresholds, reload] = useData('/thresholds');
  const [rooms] = useData('/rooms');
  const [settings, reloadSettings] = useData('/notification-settings');
  const [form, setForm] = useState(null);
  const admin = user.role === 'admin';
  const roomName = (id) => (id == null ? 'Mặc định (mọi phòng)' : rooms.find((r) => r.id === id)?.name ?? '#' + id);
  const freeRooms = rooms.filter((r) => !thresholds.some((t) => t.room_id === r.id));

  const fields = form && !form.id
    ? [{ name: 'room_id', label: 'Phòng', type: 'select', required: true, options: freeRooms.map((r) => ({ value: r.id, label: r.name })) }, ...FIELDS]
    : FIELDS;
  const save = (v) => (form.id ? api('/thresholds/' + form.id, 'PUT', v) : api('/thresholds', 'POST', v)).then(reload);
  const remove = act((t) => confirm('Xóa ngưỡng riêng của phòng (phòng sẽ dùng ngưỡng mặc định)?') && api('/thresholds/' + t.id, 'DELETE'), reload);
  const toggle = act((s, key) => api('/notification-settings/' + s.event_type, 'PUT', { [key]: !s[key] }), reloadSettings);

  const check = (s, key) => (
    <input type="checkbox" checked={!!s[key]} disabled={!admin} onChange={() => toggle(s, key)} />
  );

  return (
    <>
      <div className="row between">
        <h2>Ngưỡng môi trường</h2>
        {admin && freeRooms.length > 0 && <button onClick={() => setForm({ ...thresholds.find((t) => t.room_id == null), id: undefined, room_id: '' })}>+ Ngưỡng riêng cho phòng</button>}
      </div>
      <p className="muted">Vượt ngưỡng sẽ tạo cảnh báo; thiết bị ở chế độ tự động bật/tắt theo ngưỡng. Đèn LED bật trong khung giờ cấu hình.</p>
      <Table rows={thresholds}
        cols={[
          { label: 'Áp dụng cho', render: (t) => roomName(t.room_id) },
          { label: 'Nhiệt độ (°C)', render: (t) => `${t.temp_min} – ${t.temp_max}` },
          { label: 'Độ ẩm (%)', render: (t) => `${t.hum_min} – ${t.hum_max}` },
          { label: 'CO₂ tối đa (ppm)', key: 'co2_max' },
          { label: 'Đèn LED', render: (t) => `${t.light_on_hour}h – ${t.light_off_hour}h` },
        ]}
        actions={admin ? (t) => (
          <>
            <button className="ghost" onClick={() => setForm(t)}>Sửa</button>
            {t.room_id != null && <button className="danger" onClick={() => remove(t)}>Xóa</button>}
          </>
        ) : undefined} />

      <h2>Cài đặt thông báo</h2>
      <Table rows={settings}
        cols={[
          { label: 'Loại sự kiện', key: 'label' },
          { label: 'Bật', render: (s) => check(s, 'enabled') },
          { label: 'Gửi nhân viên', render: (s) => check(s, 'notify_staff') },
          { label: 'Gửi khách hàng', render: (s) => check(s, 'notify_customer') },
        ]} />

      {form && <FormModal title={form.id ? `Sửa ngưỡng: ${roomName(form.room_id)}` : 'Thêm ngưỡng riêng cho phòng'}
        fields={fields} initial={form} onSubmit={save} onClose={() => setForm(null)} />}
    </>
  );
}
