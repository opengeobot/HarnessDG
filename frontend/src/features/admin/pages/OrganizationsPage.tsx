/**
 * 功能: 组织管理页面。组织列表 + 创建；选中组织后在抽屉中管理成员（添加/移除）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
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
  const { t } = useTranslation();
  useDocumentTitle(t('admin.organizations.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [createOpen, setCreateOpen] = useState(false);
  const [memberOrg, setMemberOrg] = useState<OrganizationView | null>(null);
  const [createForm] = Form.useForm<CreateOrganizationRequest>();
  const [memberForm] = Form.useForm<{ principalId: string }>();

  const query = useQuery({ queryKey: ['admin', 'organizations'], queryFn: listOrganizations });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (payload: CreateOrganizationRequest) => createOrganization(payload),
    onSuccess: () => {
      message.success(t('admin.organizations.orgCreated'));
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
      message.success(t('admin.organizations.memberAdded'));
      memberForm.resetFields();
      void invalidateMembers();
    },
    onError,
  });

  const removeMemberMutation = useMutation({
    mutationFn: (principalId: string) =>
      removeOrganizationMember(memberOrg!.organizationId, principalId),
    onSuccess: () => {
      message.success(t('admin.organizations.memberRemoved'));
      void invalidateMembers();
    },
    onError,
  });

  const columns: ColumnsType<OrganizationView> = [
    {
      title: t('admin.organizations.org'),
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
      title: t('admin.organizations.giteaOrg'),
      dataIndex: 'giteaOrganization',
      key: 'giteaOrganization',
      render: (v: string) => v || '-',
    },
    { title: t('common.status'), dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => setMemberOrg(record)}>
            {t('admin.organizations.memberManagement')}
          </Button>
          <Button type="link" size="small" onClick={() => navigate(`/admin/organizations/${record.organizationId}/teams`)}>
            {t('admin.organizations.teamManagement')}
          </Button>
        </Space>
      ),
    },
  ];

  const memberColumns: ColumnsType<OrganizationMemberView> = [
    { title: t('admin.organizations.principalId'), dataIndex: 'principalId', key: 'principalId' },
    { title: t('admin.organizations.joinedAt'), dataIndex: 'joinedAt', key: 'joinedAt' },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title={t('admin.organizations.confirmRemoveMember')}
          onConfirm={() => removeMemberMutation.mutate(record.principalId)}
        >
          <Button type="link" size="small" danger>
            {t('common.remove')}
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.organizations.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            {t('admin.organizations.createOrg')}
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
        title={t('admin.organizations.createOrg')}
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
          <Form.Item name="code" label={t('admin.organizations.orgCode')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.organizations.orgCodePlaceholder')} />
          </Form.Item>
          <Form.Item name="name" label={t('admin.organizations.orgName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="giteaOrganization" label={t('admin.organizations.giteaOrg')}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={memberOrg ? t('admin.organizations.memberManagementOf', { name: memberOrg.name }) : t('admin.organizations.memberManagement')}
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
          <Form.Item name="principalId" rules={[{ required: true, message: t('admin.organizations.principalIdRequired') }]}>
            <Input placeholder={t('admin.organizations.principalIdPlaceholder')} style={{ width: 280 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={addMemberMutation.isPending}>
              {t('admin.organizations.addMember')}
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
