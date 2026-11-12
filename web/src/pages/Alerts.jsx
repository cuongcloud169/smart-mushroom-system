import React, { useState } from 'react';
import { api, fmtTime } from '../api.js';
import { act, Badge, Table, useData } from '../components/ui.jsx';

// Cảnh báo môi trường vượt ngưỡng
export default function Alerts() {
  const [showAll, setShowAll] = useState(false);
  const [rows, reload] = useData(showAll ? '/iot/alerts' : '/iot/alerts?resolved=false');
  const resolve = act((a) => api(`/iot/alerts/${a.id}/resolve`, 'PUT'), reload);
  return (
    <>
      <h2>Cảnh báo môi trường</h2>
      <label className="check"><input type="checkbox" checked={showAll} onChange={(e) => setShowAll(e.target.checked)} /> Hiện cả cảnh báo đã xử lý</label>
      <Table rows={rows}
        cols={[
          { label: 'Lúc', render: (a) => fmtTime(a.created_at) },
          { label: 'Phòng', key: 'room_name' },
          { label: 'Nội dung', key: 'message' },
          { label: 'Trạng thái', render: (a) => <Badge value={a.resolved ? 'done' : 'pending'} labels={{ done: 'Đã xử lý', pending: 'Chưa xử lý' }} /> },
        ]}
        actions={(a) => !a.resolved && <button onClick={() => resolve(a)}>Đã xử lý</button>} />
    </>
  );
}
