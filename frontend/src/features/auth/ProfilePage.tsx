/**
 * 功能: 个人中心页面。展示当前主体信息；提供修改密码表单（PUT /me/password）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { Alert, App, Button, Card, Descriptions, Form, Input, Space, Tag, Typography } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/app/auth';
import { changeCurrentUserPassword, isApiError } from '@/shared/api';
import { useDocumentTitle } from '@/shared/hooks';
import type { ChangePasswordRequest } from '@/shared/types';

interface ChangePasswordForm extends ChangePasswordRequest {
  confirmPassword: string;
}

export function ProfilePage() {
  const { t } = useTranslation();
  useDocumentTitle(t('profile.title'));
  const { message } = App.useApp();
  const { principal, reloadPrincipal } = useAuth();
  const [form] = Form.useForm<ChangePasswordForm>();
  const [submitting, setSubmitting] = useState(false);

  const mutation = useMutation({
    mutationFn: (payload: ChangePasswordRequest) => changeCurrentUserPassword(payload),
    onSuccess: async () => {
      message.success(t('profile.changeSuccess'));
      form.resetFields();
      await reloadPrincipal();
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : t('profile.changeFailed'));
    },
  });

  const handleSubmit = async (values: ChangePasswordForm) => {
    setSubmitting(true);
    try {
      await mutation.mutateAsync({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('profile.title')}
      </Typography.Title>

      {principal?.forcePasswordChange && (
        <Alert
          type="warning"
          showIcon
          icon={<ExclamationCircleOutlined />}
          message={t('profile.forceChangeAlert')}
          description={t('profile.forceChangeDesc')}
        />
      )}

      <Card title={t('profile.accountInfo')}>
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label={t('profile.principalId')}>{principal?.principalId}</Descriptions.Item>
          <Descriptions.Item label={t('profile.userId')}>{principal?.userId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('profile.loginName')}>{principal?.subject}</Descriptions.Item>
          <Descriptions.Item label={t('profile.displayName')}>{principal?.displayName ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('profile.language')}>{principal?.locale}</Descriptions.Item>
          <Descriptions.Item label={t('profile.roles')}>
            <Space size={[0, 4]} wrap>
              {(principal?.roles ?? []).map((role) => (
                <Tag key={role}>{role}</Tag>
              ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label={t('profile.scopes')}>
            <Space size={[0, 4]} wrap>
              {(principal?.scopes ?? []).map((scope) => (
                <Tag key={scope} color="blue">
                  {scope}
                </Tag>
              ))}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={t('profile.changePassword')} style={{ maxWidth: 480 }}>
        <Form<ChangePasswordForm>
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          disabled={submitting}
        >
          <Form.Item
            name="currentPassword"
            label={t('profile.currentPassword')}
            rules={[{ required: true, message: t('profile.currentPasswordRequired') }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label={t('profile.newPassword')}
            rules={[
              { required: true, message: t('profile.newPasswordRequired') },
              { min: 12, message: t('profile.newPasswordMin') },
              { max: 128, message: t('profile.newPasswordMax') },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label={t('profile.confirmNewPassword')}
            dependencies={['newPassword']}
            rules={[
              { required: true, message: t('profile.confirmNewPasswordRequired') },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error(t('profile.passwordMismatch')));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={submitting}>
              {t('common.submit')}
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Space>
  );
}
