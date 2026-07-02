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
import { useAuth } from '@/app/auth';
import { isApiError } from '@/shared/api';
import { useDocumentTitle } from '@/shared/hooks';
import type { LoginRequest } from '@/shared/types';

interface LocationState {
  from?: string;
}

export function LoginPage() {
  useDocumentTitle('登录');
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
      message.success('登录成功');
      if (principal.forcePasswordChange) {
        message.warning('首次登录请先修改密码');
        navigate('/profile', { replace: true });
        return;
      }
      navigate(from, { replace: true });
    } catch (error) {
      message.error(isApiError(error) ? error.message : '登录失败，请稍后重试');
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
          AI 资产管理平台
        </Typography.Title>
        <Form<LoginRequest> layout="vertical" onFinish={handleSubmit} disabled={submitting}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
