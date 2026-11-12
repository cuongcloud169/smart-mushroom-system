import React, { useState } from 'react';
import { api, LABELS, options } from '../api.js';
import { act, Badge, FormModal, Table, useData } from '../components/ui.jsx';

const FIELDS = [
  { name: 'name', label: 'Tên phòng', required: true },
  { name: 'description', label: 'Mô tả', type: 'textarea' },
  { name: 'capacity', label: 'Sức chứa (số khay)', type: 'number' },
  { name: 'status', label: 'Trạng thái', type: 'select', options: options(LABELS.room), required: true },
];

// Quản lý phòng nuôi: thêm, xem, sửa, xóa (xóa chỉ Admin)
export default function Rooms({ user }) {
  const [rows, reload] = useData('/rooms');
  const [form, setForm] = useState(null); // null | {} (thêm) | room (sửa)
  const save = (v) => (form.id ? api('/rooms/' + form.id, 'PUT', v) : api('/rooms', 'POST', v)).then(reload);
  const remove = act((r) => confirm(`Xóa phòng ${r.name}?`) && api('/rooms/' + r.id, 'DELETE'), reload);

  return (
    <>
      <div className="row between"><h2>Phòng nuôi</h2><button onClick={() => setForm({ status: 'active', capacity: 0 })}>+ Thêm phòng</button></div>
      <Table rows={rows}
        cols={[
          { label: 'ID', key: 'id' }, { label: 'Tên', key: 'name' }, { label: 'Mô tả', key: 'description' },
          { label: 'Sức chứa', key: 'capacity' },
          { label: 'Trạng thái', render: (r) => <Badge value={r.status} labels={LABELS.room} /> },
        ]}
        actions={(r) => (
          <>
            <button className="ghost" onClick={() => setForm(r)}>Sửa</button>
            {user.role === 'admin' && <button className="danger" onClick={() => remove(r)}>Xóa</button>}
          </>
        )} />
      {form && <FormModal title={form.id ? 'Sửa phòng' : 'Thêm phòng'} fields={FIELDS} initial={form} onSubmit={save} onClose={() => setForm(null)} />}
    </>
  );
}
