import React from 'react';
import { fmtMoney, LABELS } from '../api.js';
import { Card, useData } from '../components/ui.jsx';

// Tổng quan hệ thống
export default function Dashboard() {
  const [s] = useData('/reports/summary', null);
  if (!s) return <p>Đang tải...</p>;
  return (
    <>
      <h2>Tổng quan</h2>
      <div className="cards">
        <Card label="Phòng nuôi" value={s.rooms} />
        <Card label="Khách hàng" value={s.customers} />
        <Card label="Đang cho thuê" value={s.active_rentals} />
        <Card label="Đơn chờ duyệt" value={s.pending_rentals} />
        <Card label="Cảnh báo chưa xử lý" value={s.open_alerts} />
        <Card label="Yêu cầu nhận/giao đang mở" value={s.pending_deliveries} />
        <Card label="Doanh thu" value={fmtMoney(s.revenue_total)} />
        <Card label="ESP32 online" value={`${s.devices.online}/${s.devices.total}`} />
      </div>
      <h3>Khay theo trạng thái</h3>
      <div className="cards">
        {s.trays_by_status.length === 0 && <p className="muted">Chưa có khay</p>}
        {s.trays_by_status.map((t) => <Card key={t.status} label={LABELS.tray[t.status] ?? t.status} value={t.n} />)}
      </div>
    </>
  );
}
