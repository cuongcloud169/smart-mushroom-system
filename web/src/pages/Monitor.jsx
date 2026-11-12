import React, { useCallback, useEffect, useState } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, fmtTime, LABELS } from '../api.js';
import { Badge, Card, Table, useData } from '../components/ui.jsx';

// Giám sát nhiệt độ/độ ẩm/CO2, biểu đồ lịch sử và điều khiển thiết bị. Tự làm mới mỗi 5 giây.
export default function Monitor() {
  const [rooms] = useData('/rooms');
  const [roomId, setRoomId] = useState('');
  const [hours, setHours] = useState(24);
  const [latest, setLatest] = useState(null);
  const [history, setHistory] = useState([]);
  const [devices, setDevices] = useState([]);

  useEffect(() => { if (!roomId && rooms.length) setRoomId(String(rooms[0].id)); }, [rooms, roomId]);

  const load = useCallback(async () => {
    if (!roomId) return;
    const [l, h, d] = await Promise.all([
      api(`/iot/rooms/${roomId}/latest`),
      api(`/iot/rooms/${roomId}/history?hours=${hours}`),
      api('/iot/devices/status'),
    ]);
    setLatest(l);
    setHistory(h.map((x) => ({ ...x, time: fmtTime(x.time).slice(5), temperature: num(x.temperature), humidity: num(x.humidity), co2: num(x.co2) })));
    setDevices(d.filter((x) => x.room_id === Number(roomId)));
  }, [roomId, hours]);

  useEffect(() => {
    load().catch(() => {});
    const t = setInterval(() => load().catch(() => {}), 5000);
    return () => clearInterval(t);
  }, [load]);

  const control = async (d, body) => {
    try { await api(`/iot/devices/${d.id}/control`, 'POST', body); await load(); } catch (e) { alert(e.message); }
  };

  return (
    <>
      <h2>Giám sát & điều khiển</h2>
      <div className="row">
        <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
          {rooms.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
        </select>
        <select value={hours} onChange={(e) => setHours(Number(e.target.value))}>
          <option value={24}>24 giờ qua</option>
          <option value={72}>3 ngày qua</option>
          <option value={168}>7 ngày qua</option>
          <option value={720}>30 ngày qua</option>
        </select>
      </div>

      <div className="cards">
        <Card label="Nhiệt độ" value={latest ? `${latest.temperature ?? '--'} °C` : '--'} />
        <Card label="Độ ẩm" value={latest ? `${latest.humidity ?? '--'} %` : '--'} />
        <Card label="CO₂" value={latest ? `${latest.co2 ?? '--'} ppm` : '--'} />
        <Card label="Cập nhật lúc" value={latest ? fmtTime(latest.recorded_at).slice(11) : '--'} />
      </div>

      <h3>Lịch sử nhiệt độ và độ ẩm</h3>
      <Chart data={history} lines={[['temperature', 'Nhiệt độ (°C)', '#d9480f'], ['humidity', 'Độ ẩm (%)', '#1c7ed6']]} />
      <h3>Lịch sử CO₂ (ppm)</h3>
      <Chart data={history} lines={[['co2', 'CO₂ (ppm)', '#2f6b45']]} />

      <h3>Thiết bị trong phòng</h3>
      <Table rows={devices}
        cols={[
          { label: 'Tên', key: 'name' },
          { label: 'Loại', render: (d) => d.type === 'actuator' ? LABELS.actuator[d.actuator_kind] : LABELS.deviceType[d.type] },
          { label: 'Trạng thái', render: (d) => d.type === 'actuator' ? <Badge value={d.state ? 'on' : 'off'} labels={{ on: 'Đang bật', off: 'Tắt' }} /> : '' },
          { label: 'Chế độ', render: (d) => d.type === 'actuator' ? (d.mode === 'auto' ? 'Tự động' : 'Thủ công') : '' },
          { label: 'Kết nối', render: (d) => d.type === 'controller' ? <Badge value={d.online ? 'on' : 'off'} labels={{ on: 'Online', off: 'Offline' }} /> : '' },
        ]}
        actions={(d) => d.type === 'actuator' && (
          <>
            <button onClick={() => control(d, { state: !d.state, mode: 'manual' })}>{d.state ? 'Tắt' : 'Bật'} (thủ công)</button>
            {d.mode !== 'auto' && <button className="ghost" onClick={() => control(d, { mode: 'auto' })}>Về tự động</button>}
          </>
        )} />
    </>
  );
}

const num = (v) => (v == null ? null : Number(v));

function Chart({ data, lines }) {
  if (!data.length) return <p className="muted">Chưa có dữ liệu trong khoảng thời gian này</p>;
  return (
    <div className="chart">
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="time" fontSize={12} />
          <YAxis fontSize={12} />
          <Tooltip />
          <Legend />
          {lines.map(([key, name, color]) => <Line key={key} type="monotone" dataKey={key} name={name} stroke={color} dot={false} />)}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
