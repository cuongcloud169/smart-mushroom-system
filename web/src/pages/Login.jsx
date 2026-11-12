import React, { useState } from 'react';
import { api } from '../api.js';

// Đăng nhập cho Admin/Operator + đăng ký tài khoản Admin bằng mã mời
export default function Login({ onLogin }) {
  const [mode, setMode] = useState('login'); // login | register
  const [f, setF] = useState({ email: 'admin@mushroom.local', password: 'Admin@123', full_name: '', invite_code: '' });
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    try {
      const { token, user } = mode === 'login'
        ? await api('/auth/login', 'POST', { email: f.email, password: f.password })
        : await api('/auth/register-admin', 'POST', f);
      if (user.role === 'customer') return alert('Tài khoản khách hàng vui lòng dùng ứng dụng di động');
      localStorage.setItem('token', token);
      onLogin(user);
    } catch (err) {
      alert(err.message);
    }
  };

  return (
    <form onSubmit={submit} className="login">
      <h2>🍄 {mode === 'login' ? 'Đăng nhập quản trị' : 'Đăng ký Quản trị viên'}</h2>
      {mode === 'register' && <input placeholder="Họ tên" value={f.full_name} onChange={set('full_name')} required />}
      <input type="email" placeholder="Email" value={f.email} onChange={set('email')} required />
      <input type="password" placeholder="Mật khẩu (tối thiểu 8 ký tự)" value={f.password} onChange={set('password')} required />
      {mode === 'register' && <input placeholder="Mã mời quản trị" value={f.invite_code} onChange={set('invite_code')} required />}
      <button>{mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</button>
      <a className="link" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
        {mode === 'login' ? 'Đăng ký tài khoản Quản trị viên' : 'Đã có tài khoản? Đăng nhập'}
      </a>
    </form>
  );
}
