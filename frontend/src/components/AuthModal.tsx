// 登录/注册弹窗（09 §2.3）：左侧宣传区 + 右侧表单区，登录/注册 Tab 切换
// 时间：2026-08-21  作者：AxeXie
import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../App';

export default function AuthModal() {
  const { authModalOpen, closeLogin, login, register } = useAuth();
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  if (!authModalOpen) return null;

  function switchTab(t: 'login' | 'register') {
    setTab(t);
    setErr('');
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      if (tab === 'login') {
        await login(username.trim(), password);
      } else {
        await register(username.trim(), password, nickname.trim() || undefined);
      }
      // 成功后由 AuthContext 关闭弹窗并执行续行回调
      setUsername(''); setPassword(''); setNickname('');
    } catch (ex) {
      setErr(errMsg(ex));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-mask" onClick={closeLogin}>
      <div className="auth-modal" onClick={(e) => e.stopPropagation()}>
        <div className="auth-promo">
          <div className="auth-promo-logo">M</div>
          <h2>ModelHub 模型社区</h2>
          <p>发现、分享与部署模型、数据集与创意工作空间。</p>
          <ul>
            <li>海量开源模型与数据集目录</li>
            <li>数据驱动的筛选与全局搜索</li>
            <li>一键下载 CLI / SDK / Git 多方式</li>
          </ul>
        </div>
        <div className="auth-form-side">
          <div className="auth-modal-head">
            <div className="auth-tabs">
              <span className={`auth-tab ${tab === 'login' ? 'active' : ''}`}
                    onClick={() => switchTab('login')}>登录</span>
              <span className={`auth-tab ${tab === 'register' ? 'active' : ''}`}
                    onClick={() => switchTab('register')}>注册</span>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={closeLogin}>✕</button>
          </div>
          {err && <div className="form-error" style={{ marginBottom: 12 }}>{err}</div>}
          <form className="form" onSubmit={submit}>
            <div className="field">
              <label>用户名</label>
              <input value={username} onChange={(e) => setUsername(e.target.value)}
                     autoComplete="username" required
                     pattern="[A-Za-z0-9_\-\.]{3,32}" title="3-32位字母/数字/下划线/短横线" />
            </div>
            {tab === 'register' && (
              <div className="field">
                <label>昵称（可选）</label>
                <input value={nickname} onChange={(e) => setNickname(e.target.value)}
                       placeholder="展示名称" />
              </div>
            )}
            <div className="field">
              <label>密码</label>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                     autoComplete={tab === 'login' ? 'current-password' : 'new-password'} required />
              {tab === 'register' && <span className="hint">建议 12 位以上，含大小写字母、数字与符号（以服务端校验为准）</span>}
            </div>
            <button className="btn btn-primary" disabled={busy}>
              {busy ? '提交中…' : (tab === 'login' ? '登录' : '注册')}
            </button>
          </form>
          <div className="auth-switch">
            {tab === 'login' ? (
              <>还没有账号？<a onClick={() => switchTab('register')} style={{ cursor: 'pointer' }}>立即注册</a></>
            ) : (
              <>已有账号？<a onClick={() => switchTab('login')} style={{ cursor: 'pointer' }}>去登录</a></>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
