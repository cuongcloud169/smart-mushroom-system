import React, { useState } from 'react';
import { api, fmtTime, LABELS, options } from '../api.js';
import { act, Badge, FormModal, Table, useData } from '../components/ui.jsx';

// Quản lý thiết bị IoT, cảm biến và camera (Admin + Operator; xóa và API key chỉ Admin)
export default function Devices({ user }) {
  const [rows, reload] = useData('/iot/devices/status');
  const [rooms] = useData('/rooms');
  const [trays] = useData('/trays');
  const [form, setForm] = useState(null);
  const [preview, setPreview] = useState(null);
  const admin = user.role === 'admin';

  const fields = [
    { name: 'name', label: 'Tên thiết bị', required: true },
    { name: 'room_id', label: 'Phòng', type: 'select', required: true, options: rooms.map((r) => ({ value: r.id, label: r.name })) },
    { name: 'type', label: 'Loại', type: 'select', required: true, options: options(LABELS.deviceType) },
    { name: 'actuator_kind', label: 'Loại điều khiển (chỉ với thiết bị điều khiển)', type: 'select', options: options(LABELS.actuator) },
    { name: 'stream_url', label: 'Đường dẫn camera (snapshot/MJPEG)' },
    { name: 'tray_id', label: 'Gắn với khay (camera riêng cho khay, có thể bỏ trống)', type: 'select',
      options: trays.map((t) => ({ value: t.id, label: t.code })) },
    { name: 'is_active', label: 'Đang sử dụng', type: 'checkbox' },
  ];

  const save = (v) => (form.id ? api('/devices/' + form.id, 'PUT', v) : api('/devices', 'POST', v)).then(async (d) => {
    await reload();
    if (!form.id && d.type === 'controller' && admin) showKey(d);
  });
  const remove = act((d) => confirm(`Xóa thiết bị ${d.name}?`) && api('/devices/' + d.id, 'DELETE'), reload);
  const showKey = async (d) => {
    try { const r = await api(`/devices/${d.id}/api-key`); prompt('API key để nạp vào firmware ESP32 (DEVICE_KEY):', r.api_key); }
    catch (e) { alert(e.message); }
  };
  const regen = async (d) => {
    if (!confirm('Cấp lại API key? ESP32 đang dùng key cũ sẽ bị từ chối cho đến khi nạp key mới.')) return;
    try { const r = await api(`/devices/${d.id}/regenerate-key`, 'POST'); prompt('API key mới:', r.api_key); }
    catch (e) { alert(e.message); }
  };

  return (
    <>
      <div className="row between"><h2>Thiết bị IoT & camera</h2><button onClick={() => setForm({ is_active: true })}>+ Thêm thiết bị</button></div>
      <Table rows={rows}
        cols={[
          { label: 'Tên', key: 'name' }, { label: 'Phòng', key: 'room_name' },
          { label: 'Loại', render: (d) => LABELS.deviceType[d.type] + (d.actuator_kind ? ` - ${LABELS.actuator[d.actuator_kind]}` : '') },
          { label: 'Đang dùng', render: (d) => (d.is_active ? 'Có' : 'Không') },
          { label: 'Liên lạc cuối', render: (d) => fmtTime(d.last_seen) },
          { label: 'Kết nối', render: (d) => d.type === 'controller' ? <Badge value={d.online ? 'on' : 'off'} labels={{ on: 'Online', off: 'Offline' }} /> : '' },
        ]}
        actions={(d) => (
          <>
            {d.type === 'camera' && <button className="ghost" onClick={() => setPreview(d)}>Xem camera</button>}
            <button className="ghost" onClick={() => setForm(d)}>Sửa</button>
            {admin && d.type === 'controller' && <button className="ghost" onClick={() => showKey(d)}>API key</button>}
            {admin && d.type === 'controller' && <button className="ghost" onClick={() => regen(d)}>Cấp lại key</button>}
            {admin && <button className="danger" onClick={() => remove(d)}>Xóa</button>}
          </>
        )} />
      {form && <FormModal title={form.id ? 'Sửa thiết bị' : 'Thêm thiết bị'} fields={fields} initial={form} onSubmit={save} onClose={() => setForm(null)} />}
      {preview && (
        <div className="modal-bg" onClick={() => setPreview(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{preview.name}</h3>
            <img src={preview.stream_url} alt="camera" style={{ width: '100%' }} onError={(e) => { e.target.alt = 'Không kết nối được camera'; }} />
            <p className="muted">{preview.stream_url}</p>
            <div className="row end"><button onClick={() => setPreview(null)}>Đóng</button></div>
          </div>
        </div>
      )}
    </>
  );
}
