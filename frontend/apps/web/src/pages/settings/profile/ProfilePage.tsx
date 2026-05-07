/**
 * 功能：个人资料与修改密码页面
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useEffect } from 'react';
import { Card, Descriptions, Form, Input, Button, message, Tabs } from 'antd';
import { useTranslation } from 'react-i18next';
import { authApi, userApi } from '@/services';
import { useAuthStore } from '@/stores/authStore';

export default function ProfilePage() {
  const { t } = useTranslation(['settings', 'common']);
  const { user, setAuth, token } = useAuthStore();
  const [profileForm] = Form.useForm();
  const [pwdForm] = Form.useForm();

  useEffect(() => {
    (async () => {
      const res: any = await authApi.me();
      const u = res.data;
      if (u && token) {
        setAuth(token, u);
        profileForm.setFieldsValue({
          displayName: u.displayName,
          email: u.email,
          preferredLocale: u.preferredLocale,
        });
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onChangePassword = async () => {
    const v = await pwdForm.validateFields();
    if (v.newPassword !== v.confirmPassword) {
      message.error(t('user.password_mismatch'));
      return;
    }
    await userApi.changeMyPassword(v.oldPassword, v.newPassword);
    message.success(t('profile.password_changed'));
    pwdForm.resetFields();
  };

  return (
    <Card title={t('tabs.profile')}>
      <Tabs
        items={[
          {
            key: 'info',
            label: t('profile.basic'),
            children: (
              <Descriptions column={1} bordered size="small">
                <Descriptions.Item label={t('user.username')}>{user?.username}</Descriptions.Item>
                <Descriptions.Item label={t('user.display_name')}>{user?.displayName}</Descriptions.Item>
                <Descriptions.Item label={t('user.email')}>{user?.email}</Descriptions.Item>
                <Descriptions.Item label={t('user.preferred_locale')}>{user?.preferredLocale}</Descriptions.Item>
                <Descriptions.Item label={t('user.roles')}>{user?.roles?.join(', ')}</Descriptions.Item>
              </Descriptions>
            ),
          },
          {
            key: 'password',
            label: t('profile.change_password'),
            children: (
              <Form form={pwdForm} layout="vertical" style={{ maxWidth: 400 }}>
                <Form.Item name="oldPassword" label={t('user.old_password')} rules={[{ required: true }]}>
                  <Input.Password />
                </Form.Item>
                <Form.Item name="newPassword" label={t('user.new_password')} rules={[{ required: true, min: 6 }]}>
                  <Input.Password />
                </Form.Item>
                <Form.Item name="confirmPassword" label={t('user.confirm_password')} rules={[{ required: true }]}>
                  <Input.Password />
                </Form.Item>
                <Button type="primary" onClick={onChangePassword}>{t('profile.change_password')}</Button>
              </Form>
            ),
          },
        ]}
      />
    </Card>
  );
}
