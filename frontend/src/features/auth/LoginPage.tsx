/**
 * 功能: 登录页面。用户名密码表单，调用 useAuth().login；成功跳原目标或首页；
 *       登录后如需强制改密则引导至 /profile；错误按 ApiError message 展示。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { App, Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/app/auth';
import { isApiError } from '@/shared/api';
import { useDocumentTitle } from '@/shared/hooks';
import type { LoginRequest } from '@/shared/types';

interface LocationState {
  from?: string;
}

export function LoginPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('login.title'));
  const { message } = App.useApp();
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);

  const from = (location.state as LocationState | null)?.from ?? '/assets';

  const handleSubmit = async (values: LoginRequest) => {
    setSubmitting(true);
    try {
      const principal = await login(values);
      message.success(t('login.success'));
      if (principal.forcePasswordChange) {
        message.warning(t('login.forceChangePassword'));
        navigate('/profile', { replace: true });
        return;
      }
      navigate(from, { replace: true });
    } catch (error) {
      message.error(isApiError(error) ? error.message : t('login.failed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
        padding: 24,
      }}
    >
      <Card style={{ width: 380 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          {t('login.platformTitle')}
        </Typography.Title>
        <Form<LoginRequest> layout="vertical" onFinish={handleSubmit} disabled={submitting}>
          <Form.Item
            name="username"
            label={t('login.username')}
            rules={[{ required: true, message: t('login.usernameRequired') }]}
          >
            <Input prefix={<UserOutlined />} placeholder={t('login.username')} autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label={t('login.password')}
            rules={[{ required: true, message: t('login.passwordRequired') }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder={t('login.password')}
              autoComplete="current-password"
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              {t('login.title')}
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
