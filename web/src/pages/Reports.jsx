import React, { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { download, fmtDate } from '../api.js';
import { useData } from '../components/ui.jsx';

// Báo cáo thống kê: xem biểu đồ trực tiếp và xuất file CSV (mở bằng Excel)
export default function Reports({ user }) {
  const [days, setDays] = useState(30);
  const [roomId, setRoomId] = useState('');
  const [rooms] = useData('/rooms');
  const envPath = `/reports/environment?days=${days}${roomId ? '&room_id=' + roomId : ''}`;
  const [env] = useData(envPath);
  const [rentals] = useData('/reports/rentals');
  const [harvests] = useData('/reports/harvests');
  const [table, setTable] = useState('rentals');

  const save = (path, name) => download(path + (path.includes('?') ? '&' : '?') + 'format=csv', name + '.csv').catch((e) => alert(e.message));
  const n = (v) => (v == null ? null : Number(v));

  // Gộp sản lượng theo tháng (cộng các phòng)
  const harvestByMonth = Object.values(harvests.reduce((acc, h) => {
    acc[h.month] = acc[h.month] || { month: h.month, total_kg: 0 };
    acc[h.month].total_kg += Number(h.total_kg);
    return acc;
  }, {}));

  return (
    <>
      <h2>Báo cáo môi trường</h2>
      <div className="row">
        <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={7}>7 ngày</option><option value={30}>30 ngày</option><option value={90}>90 ngày</option>
        </select>
        <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
          <option value="">Tất cả phòng</option>
          {rooms.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
        </select>
        <button className="ghost" onClick={() => save(envPath, 'bao-cao-moi-truong')}>Xuất CSV</button>
      </div>
      <div className="chart">
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={env.map((r) => ({ ...r, day: fmtDate(r.day).slice(5), avg_temp: n(r.avg_temp), avg_humidity: n(r.avg_humidity) }))}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="day" fontSize={12} /><YAxis fontSize={12} /><Tooltip /><Legend />
            <Line dataKey="avg_temp" name="Nhiệt độ TB (°C)" stroke="#d9480f" />
            <Line dataKey="avg_humidity" name="Độ ẩm TB (%)" stroke="#1c7ed6" />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="row between"><h2>Cho thuê & doanh thu theo tháng</h2>
        <button className="ghost" onClick={() => save('/reports/rentals', 'bao-cao-cho-thue')}>Xuất CSV</button></div>
      <div className="chart">
        <ResponsiveContainer width="100%" height={260}>
          <BarChart data={rentals.map((r) => ({ ...r, revenue: n(r.revenue) / 1000 }))}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="month" fontSize={12} />
            <YAxis yAxisId="l" fontSize={12} /><YAxis yAxisId="r" orientation="right" fontSize={12} /><Tooltip /><Legend />
            <Bar yAxisId="l" dataKey="rentals" name="Số hợp đồng" fill="#2f6b45" />
            <Bar yAxisId="r" dataKey="revenue" name="Doanh thu (nghìn đ)" fill="#e8a33d" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="row between"><h2>Sản lượng thu hoạch theo tháng</h2>
        <button className="ghost" onClick={() => save('/reports/harvests', 'bao-cao-thu-hoach')}>Xuất CSV</button></div>
      <div className="chart">
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={harvestByMonth}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="month" fontSize={12} /><YAxis fontSize={12} /><Tooltip />
            <Bar dataKey="total_kg" name="Sản lượng (kg)" fill="#2f6b45" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {user.role === 'admin' && (
        <>
          <h2>Xuất dữ liệu thô</h2>
          <div className="row">
            <select value={table} onChange={(e) => setTable(e.target.value)}>
              {['users', 'rooms', 'trays', 'rentals', 'transactions', 'sensor_readings', 'alerts', 'harvest_schedules', 'delivery_requests', 'devices']
                .map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
            <button onClick={() => download('/reports/export/' + table, table + '.csv').catch((e) => alert(e.message))}>Tải CSV</button>
          </div>
        </>
      )}
    </>
  );
}
