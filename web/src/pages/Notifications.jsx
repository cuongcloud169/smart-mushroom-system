import React from 'react';
import { api, fmtTime } from '../api.js';
import { act, Table, useData } from '../components/ui.jsx';

// Thông báo của nhân viên (cảnh báo, đơn thuê mới, yêu cầu giao nhận, thiết bị offline...)
export default function Notifications({ onRead }) {
  const [rows, reload] = useData('/notifications');
  const reloadAll = () => reload().then(onRead);
  const readAll = act(() => api('/notifications/read-all', 'PUT'), reloadAll);
  const read = act((n) => api(`/notifications/${n.id}/read`, 'PUT'), reloadAll);
  return (
    <>
      <div className="row between"><h2>Thông báo</h2><button className="ghost" onClick={readAll}>Đánh dấu đã đọc tất cả</button></div>
      <Table rows={rows}
        cols={[
          { label: 'Lúc', render: (n) => fmtTime(n.created_at) },
          { label: 'Tiêu đề', render: (n) => (n.is_read ? n.title : <b>{n.title}</b>) },
          { label: 'Nội dung', key: 'body' },
        ]}
        actions={(n) => !n.is_read && <button className="ghost" onClick={() => read(n)}>Đã đọc</button>} />
    </>
  );
}
