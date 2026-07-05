/**
 * 功能: 用户管理页面。分页列表 + 创建 + 启用/禁用 + 重置密码 + 编辑资料。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Flex,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import {
  createUser,
  disableUser,
  enableUser,
  listUsers,
  resetUserPassword,
  updateUser,
} from '../api';
import type { CreateUserRequest, UserStatus, UserView } from '../types';

const STATUS_COLOR: Record<UserStatus, string> = {
  PENDING_ACTIVATION: 'gold',
  ACTIVE: 'green',
  LOCKED: 'orange',
  DISABLED: 'default',
};

const PAGE_SIZE = 20;

export function UsersPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.users.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<UserView | null>(null);
  const [resetting, setResetting] = useState<UserView | null>(null);
  const [createForm] = Form.useForm<CreateUserRequest>();
  const [editForm] = Form.useForm();
  const [resetForm] = Form.useForm<{ temporaryPassword: string }>();

  const query = useQuery({
    queryKey: ['admin', 'users', { keyword, page }],
    queryFn: () => listUsers({ keyword: keyword || undefined, page, size: PAGE_SIZE }),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (payload: CreateUserRequest) => createUser(payload),
    onSuccess: () => {
      message.success(t('admin.users.userCreated'));
      setCreateOpen(false);
      createForm.resetFields();
      void invalidate();
    },
    onError,
  });

  const updateMutation = useMutation({
    mutationFn: (vars: { userId: string; displayName: string; email?: string; locale?: string }) =>
      updateUser(vars.userId, {
        displayName: vars.displayName,
        email: vars.email || null,
        locale: vars.locale,
      }),
    onSuccess: () => {
      message.success(t('admin.users.profileUpdated'));
      setEditing(null);
      void invalidate();
    },
    onError,
  });

  const enableMutation = useMutation({
    mutationFn: (userId: string) => enableUser(userId),
    onSuccess: () => {
      message.success(t('admin.users.enabled'));
      void invalidate();
    },
    onError,
  });

  const disableMutation = useMutation({
    mutationFn: (userId: string) => disableUser(userId),
    onSuccess: () => {
      message.success(t('admin.users.disabled'));
      void invalidate();
    },
    onError,
  });

  const resetMutation = useMutation({
    mutationFn: (vars: { userId: string; temporaryPassword: string }) =>
      resetUserPassword(vars.userId, { temporaryPassword: vars.temporaryPassword }),
    onSuccess: () => {
      message.success(t('admin.users.passwordReset'));
      setResetting(null);
      resetForm.resetFields();
    },
    onError,
  });

  const columns: ColumnsType<UserView> = [
    {
      title: t('admin.users.user'),
      key: 'user',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.displayName}</Typography.Text>
          <Typography.Text type="secondary" code>
            {record.username}
          </Typography.Text>
        </Space>
      ),
    },
    { title: t('admin.users.email'), dataIndex: 'email', key: 'email', render: (email: string) => email || '-' },
    { title: t('admin.users.language'), dataIndex: 'locale', key: 'locale' },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: UserStatus) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: t('admin.users.forcePasswordChange'),
      dataIndex: 'forcePasswordChange',
      key: 'forcePasswordChange',
      render: (value: boolean) => (value ? <Tag color="red">{t('common.yes')}</Tag> : t('common.no')),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Space size="small" wrap>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              editForm.setFieldsValue({
                displayName: record.displayName,
                email: record.email ?? undefined,
                locale: record.locale,
              });
            }}
          >
            {t('common.edit')}
          </Button>
          {record.status === 'DISABLED' ? (
            <Button
              type="link"
              size="small"
              onClick={() => enableMutation.mutate(record.userId)}
            >
              {t('common.enable')}
            </Button>
          ) : (
            <Popconfirm
              title={t('admin.users.confirmDisable')}
              onConfirm={() => disableMutation.mutate(record.userId)}
            >
              <Button type="link" size="small" danger>
                {t('common.disable')}
              </Button>
            </Popconfirm>
          )}
          <Button type="link" size="small" onClick={() => setResetting(record)}>
            {t('admin.users.resetPassword')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.users.title')}
        </Typography.Title>
        <Space wrap>
          <Input.Search
            allowClear
            placeholder={t('admin.users.searchPlaceholder')}
            style={{ width: 240 }}
            onSearch={(value) => {
              setKeyword(value.trim());
              setPage(1);
            }}
          />
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            {t('admin.users.createUser')}
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<UserView>
          rowKey="userId"
          columns={columns}
          dataSource={query.data?.items ?? []}
          pagination={{
            current: page,
            pageSize: query.data?.size ?? PAGE_SIZE,
            total: query.data?.total ?? 0,
            showSizeChanger: false,
            onChange: setPage,
          }}
        />
      </QueryBoundary>

      <Modal
        title={t('admin.users.createModalTitle')}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form<CreateUserRequest>
          form={createForm}
          layout="vertical"
          onFinish={(values) => createMutation.mutate(values)}
          initialValues={{ locale: 'zh-CN' }}
          preserve={false}
        >
          <Form.Item name="username" label={t('admin.users.username')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.users.usernamePlaceholder')} />
          </Form.Item>
          <Form.Item name="displayName" label={t('admin.users.displayName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label={t('admin.users.email')} rules={[{ type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="locale" label={t('admin.users.language')}>
            <Select
              options={[
                { value: 'zh-CN', label: t('admin.users.simplifiedChinese') },
                { value: 'en-US', label: 'English' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="temporaryPassword"
            label={t('admin.users.tempPassword')}
            rules={[{ required: true }, { min: 12, message: t('admin.users.tempPasswordMin') }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('admin.users.editProfile')}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => editForm.submit()}
        confirmLoading={updateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => {
            if (!editing) {
              return;
            }
            updateMutation.mutate({ userId: editing.userId, ...values });
          }}
        >
          <Form.Item name="displayName" label={t('admin.users.displayName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label={t('admin.users.email')} rules={[{ type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="locale" label={t('admin.users.language')}>
            <Select
              options={[
                { value: 'zh-CN', label: t('admin.users.simplifiedChinese') },
                { value: 'en-US', label: 'English' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('admin.users.resetModalTitle')}
        open={resetting !== null}
        onCancel={() => setResetting(null)}
        onOk={() => resetForm.submit()}
        confirmLoading={resetMutation.isPending}
        destroyOnClose
      >
        <Form
          form={resetForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => {
            if (!resetting) {
              return;
            }
            resetMutation.mutate({ userId: resetting.userId, temporaryPassword: values.temporaryPassword });
          }}
        >
          <Form.Item
            name="temporaryPassword"
            label={t('admin.users.tempPassword')}
            rules={[{ required: true }, { min: 12, message: t('admin.users.tempPasswordMin') }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
