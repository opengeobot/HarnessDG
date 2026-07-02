/**
 * 功能: 组织管理页面。组织列表 + 创建；选中组织后在抽屉中管理成员（添加/移除）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Drawer,
  Flex,
  Form,
  Input,
  Modal,
  Popconfirm,
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
  addOrganizationMember,
  createOrganization,
  listOrganizationMembers,
  listOrganizations,
  removeOrganizationMember,
} from '../api';
import type {
  CreateOrganizationRequest,
  OrganizationMemberView,
  OrganizationView,
} from '../types';

export function OrganizationsPage() {
  useDocumentTitle('组织管理');
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [memberOrg, setMemberOrg] = useState<OrganizationView | null>(null);
  const [createForm] = Form.useForm<CreateOrganizationRequest>();
  const [memberForm] = Form.useForm<{ principalId: string }>();

  const query = useQuery({ queryKey: ['admin', 'organizations'], queryFn: listOrganizations });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : '操作失败');

  const createMutation = useMutation({
    mutationFn: (payload: CreateOrganizationRequest) => createOrganization(payload),
    onSuccess: () => {
      message.success('组织已创建');
      setCreateOpen(false);
      createForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['admin', 'organizations'] });
    },
    onError,
  });

  const membersQuery = useQuery({
    queryKey: ['admin', 'organizations', memberOrg?.organizationId, 'members'],
    queryFn: () => listOrganizationMembers(memberOrg!.organizationId),
    enabled: memberOrg !== null,
  });

  const invalidateMembers = () =>
    queryClient.invalidateQueries({
      queryKey: ['admin', 'organizations', memberOrg?.organizationId, 'members'],
    });

  const addMemberMutation = useMutation({
    mutationFn: (principalId: string) =>
      addOrganizationMember(memberOrg!.organizationId, { principalId }),
    onSuccess: () => {
      message.success('成员已添加');
      memberForm.resetFields();
      void invalidateMembers();
    },
    onError,
  });

  const removeMemberMutation = useMutation({
    mutationFn: (principalId: string) =>
      removeOrganizationMember(memberOrg!.organizationId, principalId),
    onSuccess: () => {
      message.success('成员已移除');
      void invalidateMembers();
    },
    onError,
  });

  const columns: ColumnsType<OrganizationView> = [
    {
      title: '组织',
      key: 'org',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary" code>
            {record.code}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Gitea 组织',
      dataIndex: 'giteaOrganization',
      key: 'giteaOrganization',
      render: (v: string) => v || '-',
    },
    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => setMemberOrg(record)}>
          成员管理
        </Button>
      ),
    },
  ];

  const memberColumns: ColumnsType<OrganizationMemberView> = [
    { title: '主体 ID', dataIndex: 'principalId', key: 'principalId' },
    { title: '加入时间', dataIndex: 'joinedAt', key: 'joinedAt' },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title="确认移除该成员？"
          onConfirm={() => removeMemberMutation.mutate(record.principalId)}
        >
          <Button type="link" size="small" danger>
            移除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          组织管理
        </Typography.Title>
        <Space>
          <Button onClick={() => query.refetch()}>刷新</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            创建组织
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<OrganizationView>
          rowKey="organizationId"
          columns={columns}
          dataSource={query.data ?? []}
        />
      </QueryBoundary>

      <Modal
        title="创建组织"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item name="code" label="组织代码" rules={[{ required: true }]}>
            <Input placeholder="小写字母数字与连字符" />
          </Form.Item>
          <Form.Item name="name" label="组织名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="giteaOrganization" label="Gitea 组织">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={memberOrg ? `成员管理 · ${memberOrg.name}` : '成员管理'}
        open={memberOrg !== null}
        onClose={() => setMemberOrg(null)}
        width={520}
        destroyOnClose
      >
        <Form
          form={memberForm}
          layout="inline"
          style={{ marginBottom: 16 }}
          onFinish={(values) => addMemberMutation.mutate(values.principalId.trim())}
        >
          <Form.Item name="principalId" rules={[{ required: true, message: '请输入主体 ID' }]}>
            <Input placeholder="prn_ 开头的主体 ID" style={{ width: 280 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={addMemberMutation.isPending}>
              添加成员
            </Button>
          </Form.Item>
        </Form>

        <QueryBoundary
          isLoading={membersQuery.isLoading}
          isError={membersQuery.isError}
          error={membersQuery.error}
          onRetry={() => membersQuery.refetch()}
        >
          <Table<OrganizationMemberView>
            rowKey="principalId"
            size="small"
            columns={memberColumns}
            dataSource={membersQuery.data ?? []}
          />
        </QueryBoundary>
      </Drawer>
    </Flex>
  );
}
