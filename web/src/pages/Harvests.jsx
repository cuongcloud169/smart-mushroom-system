import React, { useState } from 'react';
import { api, fmtDate, fmtTime, LABELS } from '../api.js';
import { act, Badge, FormModal, Table, useData } from '../components/ui.jsx';

// Lịch thu hoạch và nhật ký thu hoạch
export default function Harvests() {
  const [rows, reload] = useData('/harvests');
  const [trays] = useData('/trays');
  const [form, setForm] = useState(null);
  const [record, setRecord] = useState(null);

  const fields = [
    { name: 'tray_id', label: 'Khay', type: 'select', required: true,
      options: trays.filter((t) => t.status !== 'empty').map((t) => ({ value: t.id, label: `${t.code} (${LABELS.tray[t.status]})` })) },
    { name: 'planned_date', label: 'Ngày dự kiến', type: 'date', required: true },
    { name: 'note', label: 'Ghi chú', type: 'textarea' },
  ];
  const editFields = [
    { name: 'planned_date', label: 'Ngày dự kiến', type: 'date', required: true },
    { name: 'note', label: 'Ghi chú', type: 'textarea' },
  ];
  const recordFields = [
    { name: 'yield_kg', label: 'Sản lượng (kg)', type: 'number', step: '0.01', required: true },
    { name: 'note', label: 'Ghi chú', type: 'textarea' },
  ];

  const save = (v) => (form.id ? api('/harvests/' + form.id, 'PUT', v) : api('/harvests', 'POST', v)).then(reload);
  const saveRecord = (v) => api('/harvests/' + record.id, 'PUT', { ...v, status: 'harvested' }).then(reload);
  const cancel = act((h) => confirm('Hủy lịch thu hoạch?') && api('/harvests/' + h.id, 'PUT', { status: 'cancelled' }), reload);

  return (
    <>
      <div className="row between"><h2>Thu hoạch</h2><button onClick={() => setForm({})}>+ Lên lịch thu hoạch</button></div>
      <Table rows={rows}
        cols={[
          { label: 'Khay', key: 'tray_code' }, { label: 'Phòng', key: 'room_name' },
          { label: 'Ngày dự kiến', render: (h) => fmtDate(h.planned_date) },
          { label: 'Trạng thái', render: (h) => <Badge value={h.status} labels={LABELS.harvest} /> },
          { label: 'Sản lượng (kg)', key: 'yield_kg' }, { label: 'Thu hoạch lúc', render: (h) => fmtTime(h.harvested_at) },
          { label: 'Ghi chú', key: 'note' },
        ]}
        actions={(h) => h.status === 'planned' && (
          <>
            <button onClick={() => setRecord(h)}>Ghi nhận thu hoạch</button>
            <button className="ghost" onClick={() => setForm({ ...h, planned_date: fmtDate(h.planned_date) })}>Đổi lịch</button>
            <button className="danger" onClick={() => cancel(h)}>Hủy</button>
          </>
        )} />
      {form && <FormModal title={form.id ? 'Đổi lịch thu hoạch' : 'Lên lịch thu hoạch'} fields={form.id ? editFields : fields}
        initial={form} onSubmit={save} onClose={() => setForm(null)} />}
      {record && <FormModal title={`Ghi nhận thu hoạch khay ${record.tray_code}`} fields={recordFields}
        onSubmit={saveRecord} onClose={() => setRecord(null)} />}
    </>
  );
}
