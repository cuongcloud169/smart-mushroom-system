import React, { useState } from 'react';
import { api, fmtMoney } from '../api.js';
import { act, Badge, FormModal, Table, useData } from '../components/ui.jsx';

const FIELDS = [
  { name: 'name', label: 'Tên gói', required: true },
  { name: 'description', label: 'Mô tả', type: 'textarea' },
  { name: 'duration_days', label: 'Thời hạn (ngày)', type: 'number', required: true },
  { name: 'price', label: 'Giá (đồng)', type: 'number', required: true },
  { name: 'is_active', label: 'Đang bán', type: 'checkbox' },
];

// Gói thuê và giá: Admin thêm/sửa/ngừng bán; Operator chỉ xem
export default function Packages({ user }) {
  const [rows, reload] = useData('/packages');
  const [form, setForm] = useState(null);
  const admin = user.role === 'admin';
  const save = (v) => (form.id ? api('/packages/' + form.id, 'PUT', v) : api('/packages', 'POST', v)).then(reload);
  const toggle = act((p) => api('/packages/' + p.id, 'PUT', { is_active: !p.is_active }), reload);
  const remove = act((p) => confirm(`Xóa gói ${p.name}?`) && api('/packages/' + p.id, 'DELETE'), reload);

  return (
    <>
      <div className="row between"><h2>Gói thuê & giá</h2>{admin && <button onClick={() => setForm({ is_active: true })}>+ Thêm gói</button>}</div>
      <Table rows={rows}
        cols={[
          { label: 'Tên gói', key: 'name' }, { label: 'Mô tả', key: 'description' },
          { label: 'Thời hạn', render: (p) => p.duration_days + ' ngày' }, { label: 'Giá', render: (p) => fmtMoney(p.price) },
          { label: 'Trạng thái', render: (p) => <Badge value={p.is_active ? 'active' : 'inactive'} labels={{ active: 'Đang bán', inactive: 'Ngừng bán' }} /> },
        ]}
        actions={admin ? (p) => (
          <>
            <button className="ghost" onClick={() => setForm(p)}>Sửa</button>
            <button className="ghost" onClick={() => toggle(p)}>{p.is_active ? 'Ngừng bán' : 'Mở bán'}</button>
            <button className="danger" onClick={() => remove(p)}>Xóa</button>
          </>
        ) : undefined} />
      {form && <FormModal title={form.id ? 'Sửa gói thuê' : 'Thêm gói thuê'} fields={FIELDS} initial={form} onSubmit={save} onClose={() => setForm(null)} />}
    </>
  );
}
