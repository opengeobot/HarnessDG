/**
 * 功能：登录页面 - 用户登录入口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useState } from 'react';
import { Form, Input, Button, Card, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '@/services';
import { useAuthStore } from '@/stores/authStore';

interface LoginFormValues {
  username: string;
  password: string;
}

export default function LoginPage() {
  const { t } = useTranslation(['login', 'common']);
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      const res = await authApi.login(values);
      const { accessToken, refreshToken, userInfo } = res.data;

      setAuth(accessToken, userInfo);
      localStorage.setItem('refresh_token', refreshToken);

      message.success(t('login.success', '登录成功'));
      navigate('/tasks', { replace: true });
    } catch (error: any) {
      message.error(error?.message || t('login.fail', '登录失败，请检查用户名和密码'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        background: 'var(--color-bg-layout)',
      }}
    >
      <Card
        title={t('login.title', 'HarnessDG 登录')}
        style={{ width: 400, boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}
      >
        <Form<LoginFormValues>
          name="login"
          onFinish={onFinish}
          autoComplete="off"
          size="large"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: t('login.username_required', '请输入用户名') }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder={t('login.username_placeholder', '用户名')}
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: t('login.password_required', '请输入密码') }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder={t('login.password_placeholder', '密码')}
            />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              {t('login.submit', '登录')}
            </Button>
          </Form.Item>
        </Form>

        <div style={{ color: 'var(--color-text-tertiary)', fontSize: 12, textAlign: 'center' }}>
          {t('login.hint', '默认管理员账号: admin / admin123')}
        </div>
      </Card>
    </div>
  );
}
