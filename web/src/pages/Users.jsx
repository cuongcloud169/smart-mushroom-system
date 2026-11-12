import React, { useState } from 'react';
import { api, fmtDate, LABELS, options } from '../api.js';
import { act, Badge, FormModal, Table, useData } from '../components/ui.jsx';

const CREATE = [
  { name: 'email', label: 'Email', type: 'email', required: true },
  { name: 'password', label: 'Mật khẩu (tối thiểu 8 ký tự)', type: 'password', required: true },
  { name: 'full_name', label: 'Họ tên', required: true },
  { name: 'phone', label: 'Số điện thoại' },
  { name: 'address', label: 'Địa chỉ' },
  { name: 'role', label: 'Vai trò', type: 'select', required: true, options: options(LABELS.role) },
];
const EDIT = [
  { name: 'full_name', label: 'Họ tên', required: true },
  { name: 'phone', label: 'Số điện thoại' },
  { name: 'address', label: 'Địa chỉ' },
  { name: 'role', label: 'Vai trò', type: 'select', required: true, options: options(LABELS.role) },
  { name: 'is_active', label: 'Đang hoạt động', type: 'checkbox' },
];

// Quản lý tài khoản (chỉ Admin): tạo, sửa, phân quyền, vô hiệu hóa, xóa, đặt lại mật khẩu
export default function Users({ user }) {
  const [role, setRole] = useState('');
  const [rows, reload] = useData('/users' + (role ? '?role=' + role : ''));
  const [form, setForm] = useState(null);

  const save = (v) => {
    if (form.id) return api('/users/' + form.id, 'PUT', { ...v, phone: v.phone ?? '', address: v.address ?? '' }).then(reload);
    return api('/users', 'POST', v).then(reload);
  };
  const toggle = act((u) => api('/users/' + u.id, 'PUT', { is_active: !u.is_active }), reload);
  const remove = act((u) => confirm(`Xóa tài khoản ${u.email}? Thao tác không thể hoàn tác.`) && api('/users/' + u.id, 'DELETE'), reload);
  const reset = async (u) => {
    if (!confirm(`Đặt lại mật khẩu cho ${u.email}?`)) return;
    try { const r = await api(`/users/${u.id}/reset-password`, 'POST', {}); alert('Mật khẩu mới: ' + r.new_password); }
    catch (e) { alert(e.message); }
  };

  return (
    <>
      <div className="row between"><h2>Người dùng</h2><button onClick={() => setForm({ role: 'customer' })}>+ Tạo tài khoản</button></div>
      <select value={role} onChange={(e) => setRole(e.target.value)}>
        <option value="">Tất cả vai trò</option>
        {options(LABELS.role).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      <Table rows={rows}
        cols={[
          { label: 'ID', key: 'id' }, { label: 'Email', key: 'email' }, { label: 'Họ tên', key: 'full_name' },
          { label: 'SĐT', key: 'phone' }, { label: 'Vai trò', render: (u) => LABELS.role[u.role] },
          { label: 'Trạng thái', render: (u) => <Badge value={u.is_active ? 'active' : 'inactive'} labels={{ active: 'Hoạt động', inactive: 'Vô hiệu' }} /> },
          { label: 'Tạo ngày', render: (u) => fmtDate(u.created_at) },
        ]}
        actions={(u) => (
          <>
            <button className="ghost" onClick={() => setForm(u)}>Sửa</button>
            {u.id !== user.id && <button className="ghost" onClick={() => toggle(u)}>{u.is_active ? 'Vô hiệu' : 'Kích hoạt'}</button>}
            <button className="ghost" onClick={() => reset(u)}>Đặt lại MK</button>
            {u.id !== user.id && <button className="danger" onClick={() => remove(u)}>Xóa</button>}
          </>
        )} />
      {form && <FormModal title={form.id ? `Sửa tài khoản ${form.email}` : 'Tạo tài khoản'} fields={form.id ? EDIT : CREATE}
        initial={form} onSubmit={save} onClose={() => setForm(null)} />}
    </>
  );
}
