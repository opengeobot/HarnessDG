/**
 * 功能: 个人中心页面。展示当前主体信息；提供修改密码表单（PUT /me/password）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { Alert, App, Button, Card, Descriptions, Form, Input, Space, Tag, Typography } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { useAuth } from '@/app/auth';
import { changeCurrentUserPassword, isApiError } from '@/shared/api';
import { useDocumentTitle } from '@/shared/hooks';
import type { ChangePasswordRequest } from '@/shared/types';

interface ChangePasswordForm extends ChangePasswordRequest {
  confirmPassword: string;
}

export function ProfilePage() {
  useDocumentTitle('个人中心');
  const { message } = App.useApp();
  const { principal, reloadPrincipal } = useAuth();
  const [form] = Form.useForm<ChangePasswordForm>();
  const [submitting, setSubmitting] = useState(false);

  const mutation = useMutation({
    mutationFn: (payload: ChangePasswordRequest) => changeCurrentUserPassword(payload),
    onSuccess: async () => {
      message.success('密码修改成功');
      form.resetFields();
      await reloadPrincipal();
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : '密码修改失败');
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
        个人中心
      </Typography.Title>

      {principal?.forcePasswordChange && (
        <Alert
          type="warning"
          showIcon
          icon={<ExclamationCircleOutlined />}
          message="首次登录必须修改密码"
          description="检测到当前密码为初始密码，请修改密码后方可使用平台功能。"
        />
      )}

      <Card title="账户信息">
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="主体 ID">{principal?.principalId}</Descriptions.Item>
          <Descriptions.Item label="用户 ID">{principal?.userId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="登录名">{principal?.subject}</Descriptions.Item>
          <Descriptions.Item label="显示名">{principal?.displayName ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="语言">{principal?.locale}</Descriptions.Item>
          <Descriptions.Item label="角色">
            <Space size={[0, 4]} wrap>
              {(principal?.roles ?? []).map((role) => (
                <Tag key={role}>{role}</Tag>
              ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="权限 Scope">
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

      <Card title="修改密码" style={{ maxWidth: 480 }}>
        <Form<ChangePasswordForm>
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          disabled={submitting}
        >
          <Form.Item
            name="currentPassword"
            label="当前密码"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 12, message: '密码至少 12 位' },
              { max: 128, message: '密码不超过 128 位' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={submitting}>
              提交
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Space>
  );
}
