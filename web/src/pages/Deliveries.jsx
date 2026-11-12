import React from 'react';
import { api, fmtTime, LABELS } from '../api.js';
import { act, Badge, Table, useData } from '../components/ui.jsx';

const NEXT = { pending: 'confirmed', confirmed: 'delivering', delivering: 'done' };

// Yêu cầu nhận tại trang trại / giao tận nơi
export default function Deliveries() {
  const [rows, reload] = useData('/deliveries');
  const set = act((d, status) => api(`/deliveries/${d.id}/status`, 'PUT', { status }), reload);
  return (
    <>
      <h2>Nhận / giao nấm</h2>
      <Table rows={rows}
        cols={[
          { label: 'Lúc', render: (d) => fmtTime(d.created_at) }, { label: 'Khách', key: 'customer_name' },
          { label: 'SĐT', key: 'customer_phone' }, { label: 'Khay', key: 'tray_code' },
          { label: 'Hình thức', render: (d) => (d.type === 'pickup' ? 'Nhận tại trang trại' : 'Giao tận nơi') },
          { label: 'Địa chỉ', key: 'address' }, { label: 'Ghi chú', key: 'note' },
          { label: 'Trạng thái', render: (d) => <Badge value={d.status} labels={LABELS.delivery} /> },
        ]}
        actions={(d) => NEXT[d.status] && (
          <>
            <button onClick={() => set(d, NEXT[d.status])}>→ {LABELS.delivery[NEXT[d.status]]}</button>
            <button className="danger" onClick={() => set(d, 'cancelled')}>Hủy</button>
          </>
        )} />
    </>
  );
}
