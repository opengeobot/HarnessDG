// 注册页
// 时间：2026-08-21  作者：AxeXie
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../App';

export default function RegisterPage() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [username, setUsername] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      await register(username.trim(), password, nickname.trim() || undefined);
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
        <h1 className="auth-title">注册 ModelHub</h1>
        <p className="auth-sub">创建账号，分享你的模型与数据集</p>
        {err && <div className="form-error">{err}</div>}
        <form className="form" onSubmit={submit}>
          <div className="field">
            <label>用户名</label>
            <input value={username} onChange={(e) => setUsername(e.target.value)}
                   autoComplete="username" required
                   pattern="[A-Za-z0-9_\-\.]{3,32}" title="3-32位字母/数字/下划线/短横线" />
          </div>
          <div className="field">
            <label>昵称（可选）</label>
            <input value={nickname} onChange={(e) => setNickname(e.target.value)}
                   placeholder="展示名称" />
          </div>
          <div className="field">
            <label>密码</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                   autoComplete="new-password" required minLength={10} />
            <span className="hint">至少 10 位，建议含大小写字母、数字与符号</span>
          </div>
          <button className="btn btn-primary" disabled={busy}>{busy ? '注册中…' : '注册'}</button>
        </form>
        <div className="auth-switch">
          已有账号？<Link to="/login">去登录</Link>
        </div>
      </div>
    </div>
  );
}
