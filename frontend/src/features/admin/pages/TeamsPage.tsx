/**
 * 功能: Team 管理页面。在组织上下文下列出 Team 列表，支持创建 Team、编辑、成员管理（添加/移除）。
 *       组织可来自路由参数（从组织页进入）或页面内选择器（从侧边导航直接进入），
 *       使该页面在不依赖组织列表跳转的情况下也可被发现与使用。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import {
  App,
  Button,
  Drawer,
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
  addTeamMember,
  createTeam,
  listOrganizations,
  listTeamMembers,
  listTeams,
  removeTeamMember,
  updateTeam,
} from '../api';
import type {
  AddTeamMemberRequest,
  CreateTeamRequest,
  TeamMemberView,
  TeamView,
  UpdateTeamRequest,
} from '../types';

export function TeamsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.teams.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { orgId: routeOrgId } = useParams<{ orgId: string }>();

  // 组织来源二选一：路由参数优先（从组织页进入），否则用页面内选择器（从导航直接进入）。
  const [selectedOrgId, setSelectedOrgId] = useState<string | undefined>();
  const orgId = routeOrgId ?? selectedOrgId;

  const [createOpen, setCreateOpen] = useState(false);
  const [editTeam, setEditTeam] = useState<TeamView | null>(null);
  const [memberTeam, setMemberTeam] = useState<TeamView | null>(null);
  const [createForm] = Form.useForm<CreateTeamRequest>();
  const [editForm] = Form.useForm<UpdateTeamRequest>();
  const [memberForm] = Form.useForm<AddTeamMemberRequest>();

  const orgsQuery = useQuery({
    queryKey: ['admin', 'organizations'],
    queryFn: listOrganizations,
    // 已有路由参数时无需再拉组织列表。
    enabled: !routeOrgId,
  });

  const teamsQuery = useQuery({
    queryKey: ['admin', 'organizations', orgId, 'teams'],
    queryFn: () => listTeams(orgId!),
    enabled: Boolean(orgId),
  });

  const membersQuery = useQuery({
    queryKey: ['admin', 'organizations', orgId, 'teams', memberTeam?.teamId, 'members'],
    queryFn: () => listTeamMembers(orgId!, memberTeam!.teamId),
    enabled: Boolean(orgId && memberTeam),
  });

  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const invalidateTeams = () =>
    queryClient.invalidateQueries({ queryKey: ['admin', 'organizations', orgId, 'teams'] });
  const invalidateMembers = () =>
    queryClient.invalidateQueries({
      queryKey: ['admin', 'organizations', orgId, 'teams', memberTeam?.teamId, 'members'],
    });

  const createMutation = useMutation({
    mutationFn: (payload: CreateTeamRequest) => createTeam(orgId!, payload),
    onSuccess: () => {
      message.success(t('admin.teams.teamCreated'));
      setCreateOpen(false);
      createForm.resetFields();
      invalidateTeams();
    },
    onError,
  });

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateTeamRequest) => updateTeam(orgId!, editTeam!.teamId, payload),
    onSuccess: () => {
      message.success(t('admin.teams.teamUpdated'));
      setEditTeam(null);
      editForm.resetFields();
      invalidateTeams();
    },
    onError,
  });

  const addMemberMutation = useMutation({
    mutationFn: (payload: AddTeamMemberRequest) =>
      addTeamMember(orgId!, memberTeam!.teamId, payload),
    onSuccess: () => {
      message.success(t('admin.teams.memberAdded'));
      memberForm.resetFields();
      invalidateMembers();
    },
    onError,
  });

  const removeMemberMutation = useMutation({
    mutationFn: (principalId: string) =>
      removeTeamMember(orgId!, memberTeam!.teamId, principalId),
    onSuccess: () => {
      message.success(t('admin.teams.memberRemoved'));
      invalidateMembers();
    },
    onError,
  });

  const columns: ColumnsType<TeamView> = [
    { title: t('admin.teams.teamName'), dataIndex: 'name', key: 'name' },
    { title: t('common.description'), dataIndex: 'description', key: 'description', render: (v: string) => v || '-' },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>,
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditTeam(record);
              editForm.setFieldsValue({ name: record.name, description: record.description });
            }}
          >
            {t('common.edit')}
          </Button>
          <Button type="link" size="small" onClick={() => setMemberTeam(record)}>
            {t('admin.teams.memberManagement')}
          </Button>
        </Space>
      ),
    },
  ];

  const memberColumns: ColumnsType<TeamMemberView> = [
    { title: t('admin.teams.principalId'), dataIndex: 'principalId', key: 'principalId' },
    { title: t('admin.teams.role'), dataIndex: 'role', key: 'role' },
    { title: t('admin.teams.joinedAt'), dataIndex: 'joinedAt', key: 'joinedAt' },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title={t('admin.teams.confirmRemoveMember')}
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
        <Space>
          <Button onClick={() => navigate('/admin/organizations')}>{t('common.back')}</Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('admin.teams.title')}
          </Typography.Title>
        </Space>
        <Space wrap>
          {/* 从侧边导航直接进入时没有路由组织参数，用选择器显式选定组织上下文 */}
          {!routeOrgId && (
            <Select
              style={{ width: 260 }}
              placeholder={t('admin.projects.selectOrg')}
              loading={orgsQuery.isLoading}
              value={selectedOrgId}
              onChange={setSelectedOrgId}
              options={(orgsQuery.data ?? []).map((org) => ({
                value: org.organizationId,
                label: `${org.name} (${org.code})`,
              }))}
            />
          )}
          <Button onClick={() => teamsQuery.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" disabled={!orgId} onClick={() => setCreateOpen(true)}>
            {t('admin.teams.createTeam')}
          </Button>
        </Space>
      </Flex>

      {orgId ? (
        <QueryBoundary
          isLoading={teamsQuery.isLoading}
          isError={teamsQuery.isError}
          error={teamsQuery.error}
          onRetry={() => teamsQuery.refetch()}
        >
          <Table<TeamView>
            rowKey="teamId"
            columns={columns}
            dataSource={teamsQuery.data ?? []}
          />
        </QueryBoundary>
      ) : (
        <Typography.Text type="secondary">{t('admin.teams.selectOrgHint')}</Typography.Text>
      )}

      {/* 创建 Team */}
      <Modal
        title={t('admin.teams.createTeam')}
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
          <Form.Item name="name" label={t('admin.teams.teamName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑 Team */}
      <Modal
        title={t('admin.teams.editTeam')}
        open={editTeam !== null}
        onCancel={() => setEditTeam(null)}
        onOk={() => editForm.submit()}
        confirmLoading={updateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => updateMutation.mutate(values)}
        >
          <Form.Item name="name" label={t('admin.teams.teamName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="status" label={t('common.status')}>
            <Select
              options={[
                { value: 'ACTIVE', label: t('admin.users.enabled') },
                { value: 'DISABLED', label: t('admin.users.disabled') },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 成员管理 */}
      <Drawer
        title={
          memberTeam
            ? t('admin.teams.memberManagementOf', { name: memberTeam.name })
            : t('admin.teams.memberManagement')
        }
        open={memberTeam !== null}
        onClose={() => setMemberTeam(null)}
        width={560}
        destroyOnClose
      >
        <Form
          form={memberForm}
          layout="inline"
          style={{ marginBottom: 16 }}
          onFinish={(values) => addMemberMutation.mutate(values)}
        >
          <Form.Item name="principalId" rules={[{ required: true, message: t('admin.organizations.principalIdRequired') }]}>
            <Input placeholder={t('admin.organizations.principalIdPlaceholder')} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="role" initialValue="MEMBER">
            <Select style={{ width: 120 }} options={[
              { value: 'MEMBER', label: 'Member' },
              { value: 'LEAD', label: 'Lead' },
            ]} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={addMemberMutation.isPending}>
              {t('admin.teams.addMember')}
            </Button>
          </Form.Item>
        </Form>

        <QueryBoundary
          isLoading={membersQuery.isLoading}
          isError={membersQuery.isError}
          error={membersQuery.error}
          onRetry={() => membersQuery.refetch()}
        >
          <Table<TeamMemberView>
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
