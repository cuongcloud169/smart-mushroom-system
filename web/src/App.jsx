import React, { useCallback, useEffect, useState } from 'react';
import { api, LABELS } from './api.js';
import Login from './pages/Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Rooms from './pages/Rooms.jsx';
import Trays from './pages/Trays.jsx';
import Rentals from './pages/Rentals.jsx';
import Packages from './pages/Packages.jsx';
import Harvests from './pages/Harvests.jsx';
import Deliveries from './pages/Deliveries.jsx';
import Reports from './pages/Reports.jsx';
import Users from './pages/Users.jsx';
import Notifications from './pages/Notifications.jsx';

// Menu: [khóa, tên hiển thị, component, các vai trò được thấy]
const STAFF = ['admin', 'operator'];
const MENU = [
  ['dash', 'Tổng quan', Dashboard, STAFF],
  ['notifications', 'Thông báo', Notifications, STAFF],
  ['rooms', 'Phòng nuôi', Rooms, STAFF],
  ['trays', 'Khay trồng', Trays, STAFF],
  ['rentals', 'Cho thuê', Rentals, STAFF],
  ['packages', 'Gói thuê & giá', Packages, STAFF],
  ['harvests', 'Thu hoạch', Harvests, STAFF],
  ['deliveries', 'Nhận / giao nấm', Deliveries, STAFF],
  ['reports', 'Báo cáo', Reports, STAFF],
  ['users', 'Người dùng', Users, ['admin']],
];

export default function App() {
  const [user, setUser] = useState(null);
  const [checking, setChecking] = useState(!!localStorage.getItem('token'));
  const [page, setPage] = useState('dash');
  const [unread, setUnread] = useState(0);

  // Mở lại trang vẫn còn đăng nhập nếu token còn hạn
  useEffect(() => {
    if (!localStorage.getItem('token')) return;
    api('/auth/me').then(setUser).catch(() => localStorage.removeItem('token')).finally(() => setChecking(false));
  }, []);

  const loadUnread = useCallback(() => api('/notifications/unread-count').then((r) => setUnread(r.count)).catch(() => {}), []);
  useEffect(() => {
    if (!user) return undefined;
    loadUnread();
    const t = setInterval(loadUnread, 15000);
    return () => clearInterval(t);
  }, [user, loadUnread]);

  if (checking) return <p style={{ padding: 20 }}>Đang tải...</p>;
  if (!user) return <Login onLogin={setUser} />;

  const items = MENU.filter((m) => m[3].includes(user.role));
  const current = items.find((m) => m[0] === page) || items[0];
  const Page = current[2];
  const logout = () => { localStorage.removeItem('token'); setUser(null); };

  return (
    <div className="layout">
      <nav>
        <h3>🍄 Nuôi nấm thông minh</h3>
        {items.map(([k, label]) => (
          <a key={k} className={k === current[0] ? 'on' : ''} onClick={() => setPage(k)}>
            {label}{k === 'notifications' && unread > 0 && <span className="count">{unread}</span>}
          </a>
        ))}
        <hr />
        <small>{user.full_name}<br />{LABELS.role[user.role]}</small>
        <a onClick={logout}>Đăng xuất</a>
      </nav>
      <main><Page key={current[0]} user={user} onRead={loadUnread} /></main>
    </div>
  );
}
