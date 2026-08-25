// 登录/注册弹窗（09 §2.3）：左侧宣传区 + 右侧表单区，登录/注册 Tab 切换
// 时间：2026-08-21  作者：AxeXie
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../App';

export default function AuthModal() {
  const { authModalOpen, closeLogin, login, register } = useAuth();
  const { t } = useTranslation();
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
          <h2>{t('auth.title')}</h2>
          <p>{t('auth.subtitle')}</p>
          <ul>
            <li>{t('auth.feature1')}</li>
            <li>{t('auth.feature2')}</li>
            <li>{t('auth.feature3')}</li>
          </ul>
        </div>
        <div className="auth-form-side">
          <div className="auth-modal-head">
            <div className="auth-tabs">
              <span className={`auth-tab ${tab === 'login' ? 'active' : ''}`}
                    onClick={() => switchTab('login')}>{t('auth.loginTab')}</span>
              <span className={`auth-tab ${tab === 'register' ? 'active' : ''}`}
                    onClick={() => switchTab('register')}>{t('auth.registerTab')}</span>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={closeLogin}>✕</button>
          </div>
          {err && <div className="form-error" style={{ marginBottom: 12 }}>{err}</div>}
          <form className="form" onSubmit={submit}>
            <div className="field">
              <label>{t('auth.username')}</label>
              <input value={username} onChange={(e) => setUsername(e.target.value)}
                     autoComplete="username" required
                     pattern="[A-Za-z0-9_\-\.]{3,32}" title={t('auth.usernamePattern')} />
            </div>
            {tab === 'register' && (
              <div className="field">
                <label>{t('auth.nickname')}</label>
                <input value={nickname} onChange={(e) => setNickname(e.target.value)}
                       placeholder={t('auth.nicknamePlaceholder')} />
              </div>
            )}
            <div className="field">
              <label>{t('auth.password')}</label>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                     autoComplete={tab === 'login' ? 'current-password' : 'new-password'} required />
              {tab === 'register' && <span className="hint">{t('auth.passwordHint')}</span>}
            </div>
            <button className="btn btn-primary" disabled={busy}>
              {busy ? t('auth.submitting') : (tab === 'login' ? t('auth.loginTab') : t('auth.registerTab'))}
            </button>
          </form>
          <div className="auth-switch">
            {tab === 'login' ? (
              <>{t('auth.noAccount')}<a onClick={() => switchTab('register')} style={{ cursor: 'pointer' }}>{t('auth.registerNow')}</a></>
            ) : (
              <>{t('auth.hasAccount')}<a onClick={() => switchTab('login')} style={{ cursor: 'pointer' }}>{t('auth.goLogin')}</a></>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
