import React, { useState } from 'react';
import { api, fmtDate, fmtMoney, fmtTime, LABELS } from '../api.js';
import { act, Badge, FormModal, Table, useData } from '../components/ui.jsx';

// Cho thuê: duyệt đơn, bàn giao khay, hoàn tất/hủy hợp đồng, lịch sử giao dịch
export default function Rentals() {
  const [tab, setTab] = useState('rentals');
  const [rows, reload] = useData('/rentals');
  const [txs, reloadTx] = useData('/rentals/transactions');
  const [status, setStatus] = useState('');
  const [assign, setAssign] = useState(false);
  const [trays, reloadTrays] = useData(assign ? '/trays/available' : null);
  const [packages] = useData('/packages');
  const [customers] = useData(assign ? '/users/customers' : null);

  const reloadAll = () => Promise.all([reload(), reloadTx(), reloadTrays()]);
  const approve = act((r) => api(`/rentals/${r.id}/approve`, 'PUT'), reloadAll);
  const close = act((r, s) => confirm(s === 'cancelled' ? 'Hủy hợp đồng này?' : 'Hoàn tất hợp đồng này?')
    && api(`/rentals/${r.id}/status`, 'PUT', { status: s }), reloadAll);

  const assignFields = [
    { name: 'tray_id', label: 'Khay trống', type: 'select', required: true, options: trays.map((t) => ({ value: t.id, label: `${t.code} - ${t.room_name}` })) },
    { name: 'package_id', label: 'Gói thuê', type: 'select', required: true,
      options: packages.filter((p) => p.is_active).map((p) => ({ value: p.id, label: `${p.name} (${fmtMoney(p.price)})` })) },
    { name: 'customer_id', label: 'Khách hàng (bỏ trống: khách tự nhập mã khay)', type: 'select',
      options: customers.map((c) => ({ value: c.id, label: `${c.full_name} - ${c.email}` })) },
  ];

  return (
    <>
      <div className="row between">
        <h2>Cho thuê khay</h2>
        <button onClick={() => setAssign(true)}>+ Bàn giao khay</button>
      </div>
      <div className="tabs">
        <a className={tab === 'rentals' ? 'on' : ''} onClick={() => setTab('rentals')}>Hợp đồng thuê</a>
        <a className={tab === 'tx' ? 'on' : ''} onClick={() => setTab('tx')}>Giao dịch</a>
      </div>

      {tab === 'rentals' ? (
        <>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Tất cả trạng thái</option>
            {Object.entries(LABELS.rental).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <Table rows={rows.filter((r) => !status || r.status === status)}
            cols={[
              { label: 'ID', key: 'id' }, { label: 'Khay', key: 'tray_code' },
              { label: 'Khách', render: (r) => r.customer_name ?? <i className="muted">chờ khách nhập mã</i> },
              { label: 'Gói', key: 'package_name' }, { label: 'Giá', render: (r) => fmtMoney(r.total_price) },
              { label: 'Bắt đầu', render: (r) => fmtDate(r.start_date) }, { label: 'Hết hạn', render: (r) => fmtDate(r.end_date) },
              { label: 'Trạng thái', render: (r) => <Badge value={r.status} labels={LABELS.rental} /> },
            ]}
            actions={(r) => (
              <>
                {r.status === 'pending' && <button onClick={() => approve(r)}>Duyệt</button>}
                {r.status === 'active' && <button className="ghost" onClick={() => close(r, 'completed')}>Hoàn tất</button>}
                {['pending', 'active'].includes(r.status) && <button className="danger" onClick={() => close(r, 'cancelled')}>Hủy</button>}
              </>
            )} />
        </>
      ) : (
        <Table rows={txs}
          cols={[
            { label: 'Lúc', render: (t) => fmtTime(t.created_at) }, { label: 'Khách', key: 'customer_name' },
            { label: 'Khay', key: 'tray_code' }, { label: 'Số tiền', render: (t) => fmtMoney(t.amount) },
            { label: 'Trạng thái', render: (t) => <Badge value={t.status} labels={LABELS.tx} /> },
          ]} />
      )}

      {assign && <FormModal title="Bàn giao khay cho khách" fields={assignFields} submitText="Bàn giao"
        onSubmit={(v) => api('/rentals/assign', 'POST', v).then(reloadAll)} onClose={() => setAssign(false)} />}
    </>
  );
}
