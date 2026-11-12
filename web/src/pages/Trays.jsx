import React, { useState } from 'react';
import { api, fmtDate, LABELS, options } from '../api.js';
import { act, Badge, FormModal, Table, useData } from '../components/ui.jsx';

// Quản lý khay: thêm/sửa/xóa, lọc, cập nhật tiến độ (khách nhận thông báo)
export default function Trays({ user }) {
  const [rows, reload] = useData('/trays');
  const [rooms] = useData('/rooms');
  const [filter, setFilter] = useState({ room: '', status: '' });
  const [form, setForm] = useState(null);
  const [progress, setProgress] = useState(null);

  const roomOpts = rooms.map((r) => ({ value: r.id, label: r.name }));
  const roomName = (id) => rooms.find((r) => r.id === id)?.name ?? id;
  const fields = [
    { name: 'code', label: 'Mã khay (bỏ trống để tự sinh)' },
    { name: 'room_id', label: 'Phòng', type: 'select', options: roomOpts, required: true },
    { name: 'mushroom_type', label: 'Loại nấm' },
    { name: 'status', label: 'Trạng thái', type: 'select', options: options(LABELS.tray), required: true },
  ];
  const progressFields = [
    { name: 'growth_stage', label: 'Giai đoạn (vd: cấy giống, ủ tơ, ra quả thể)' },
    { name: 'status', label: 'Trạng thái', type: 'select', options: options(LABELS.tray) },
    { name: 'fill_percent', label: '% lấp đầy (0-100)', type: 'number' },
    { name: 'expected_harvest_date', label: 'Dự kiến thu hoạch', type: 'date' },
    { name: 'note', label: 'Ghi chú gửi khách', type: 'textarea' },
  ];

  const save = (v) => (form.id ? api('/trays/' + form.id, 'PUT', v) : api('/trays', 'POST', v)).then(reload);
  const saveProgress = (v) => api(`/trays/${progress.id}/progress`, 'PUT', v).then(reload);
  const remove = act((t) => confirm(`Xóa khay ${t.code}?`) && api('/trays/' + t.id, 'DELETE'), reload);

  const shown = rows.filter((t) => (!filter.room || t.room_id === Number(filter.room)) && (!filter.status || t.status === filter.status));

  return (
    <>
      <div className="row between"><h2>Khay trồng</h2><button onClick={() => setForm({ status: 'empty' })}>+ Thêm khay</button></div>
      <div className="row">
        <select value={filter.room} onChange={(e) => setFilter({ ...filter, room: e.target.value })}>
          <option value="">Tất cả phòng</option>
          {roomOpts.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })}>
          <option value="">Tất cả trạng thái</option>
          {options(LABELS.tray).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>
      <Table rows={shown}
        cols={[
          { label: 'Mã khay', key: 'code' },
          { label: 'Phòng', render: (t) => roomName(t.room_id) },
          { label: 'Loại nấm', key: 'mushroom_type' },
          { label: 'Trạng thái', render: (t) => <Badge value={t.status} labels={LABELS.tray} /> },
          { label: 'Giai đoạn', key: 'growth_stage' },
          { label: '% đầy', key: 'fill_percent' },
          { label: 'Dự kiến thu hoạch', render: (t) => fmtDate(t.expected_harvest_date) },
        ]}
        actions={(t) => (
          <>
            <button onClick={() => setProgress(t)}>Cập nhật tiến độ</button>
            <button className="ghost" onClick={() => setForm(t)}>Sửa</button>
            {user.role === 'admin' && <button className="danger" onClick={() => remove(t)}>Xóa</button>}
          </>
        )} />
      {form && <FormModal title={form.id ? 'Sửa khay' : 'Thêm khay'} fields={fields} initial={form} onSubmit={save} onClose={() => setForm(null)} />}
      {progress && <FormModal title={`Cập nhật tiến độ khay ${progress.code}`} fields={progressFields}
        initial={{ growth_stage: progress.growth_stage, status: progress.status, fill_percent: progress.fill_percent,
                   expected_harvest_date: fmtDate(progress.expected_harvest_date) }}
        onSubmit={saveProgress} onClose={() => setProgress(null)} />}
    </>
  );
}
