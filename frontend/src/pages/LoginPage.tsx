// 登录页
// 时间：2026-08-21  作者：AxeXie
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../App';

export default function LoginPage() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      await login(username.trim(), password);
      nav('/');
    } catch (ex) {
      setErr(errMsg(ex));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="card card-pad">
        <h1 className="auth-title">登录 ModelHub</h1>
        <p className="auth-sub">登录以管理你的仓库与模型</p>
        {err && <div className="form-error">{err}</div>}
        <form className="form" onSubmit={submit}>
          <div className="field">
            <label>用户名</label>
            <input value={username} onChange={(e) => setUsername(e.target.value)}
                   autoComplete="username" required />
          </div>
          <div className="field">
            <label>密码</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                   autoComplete="current-password" required />
          </div>
          <button className="btn btn-primary" disabled={busy}>{busy ? '登录中…' : '登录'}</button>
        </form>
        <div className="auth-switch">
          还没有账号？<Link to="/register">立即注册</Link>
        </div>
      </div>
    </div>
  );
}
