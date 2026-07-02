/**
 * 功能: 用户管理页面。分页列表 + 创建 + 启用/禁用 + 重置密码 + 编辑资料。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
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
  useDocumentTitle('用户管理');
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
    message.error(isApiError(error) ? error.message : '操作失败');

  const createMutation = useMutation({
    mutationFn: (payload: CreateUserRequest) => createUser(payload),
    onSuccess: () => {
      message.success('用户已创建');
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
      message.success('资料已更新');
      setEditing(null);
      void invalidate();
    },
    onError,
  });

  const enableMutation = useMutation({
    mutationFn: (userId: string) => enableUser(userId),
    onSuccess: () => {
      message.success('已启用');
      void invalidate();
    },
    onError,
  });

  const disableMutation = useMutation({
    mutationFn: (userId: string) => disableUser(userId),
    onSuccess: () => {
      message.success('已禁用');
      void invalidate();
    },
    onError,
  });

  const resetMutation = useMutation({
    mutationFn: (vars: { userId: string; temporaryPassword: string }) =>
      resetUserPassword(vars.userId, { temporaryPassword: vars.temporaryPassword }),
    onSuccess: () => {
      message.success('已重置临时密码');
      setResetting(null);
      resetForm.resetFields();
    },
    onError,
  });

  const columns: ColumnsType<UserView> = [
    {
      title: '用户',
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
    { title: '邮箱', dataIndex: 'email', key: 'email', render: (email: string) => email || '-' },
    { title: '语言', dataIndex: 'locale', key: 'locale' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: UserStatus) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: '强制改密',
      dataIndex: 'forcePasswordChange',
      key: 'forcePasswordChange',
      render: (value: boolean) => (value ? <Tag color="red">是</Tag> : '否'),
    },
    {
      title: '操作',
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
            编辑
          </Button>
          {record.status === 'DISABLED' ? (
            <Button
              type="link"
              size="small"
              onClick={() => enableMutation.mutate(record.userId)}
            >
              启用
            </Button>
          ) : (
            <Popconfirm
              title="确认禁用该用户？其 Token 将被吊销。"
              onConfirm={() => disableMutation.mutate(record.userId)}
            >
              <Button type="link" size="small" danger>
                禁用
              </Button>
            </Popconfirm>
          )}
          <Button type="link" size="small" onClick={() => setResetting(record)}>
            重置密码
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          用户管理
        </Typography.Title>
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="搜索用户名/显示名"
            style={{ width: 240 }}
            onSearch={(value) => {
              setKeyword(value.trim());
              setPage(1);
            }}
          />
          <Button onClick={() => query.refetch()}>刷新</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            创建用户
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
        title="创建用户"
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
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input placeholder="字母数字与 . _ -" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="locale" label="语言">
            <Select
              options={[
                { value: 'zh-CN', label: '简体中文' },
                { value: 'en-US', label: 'English' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="temporaryPassword"
            label="临时密码"
            rules={[{ required: true }, { min: 12, message: '至少 12 位' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑用户资料"
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
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="locale" label="语言">
            <Select
              options={[
                { value: 'zh-CN', label: '简体中文' },
                { value: 'en-US', label: 'English' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重置密码"
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
            label="临时密码"
            rules={[{ required: true }, { min: 12, message: '至少 12 位' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
