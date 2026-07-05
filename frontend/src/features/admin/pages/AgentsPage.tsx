/**
 * 功能: Agent 管理页面。列表 + 注册（创建响应含一次性凭据）+ 启用/禁用 + Tool 白名单编辑。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Alert,
  Button,
  Flex,
  Form,
  Input,
  InputNumber,
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
  createAgent,
  disableAgent,
  enableAgent,
  listAgents,
  updateAgentToolAllowlist,
} from '../api';
import type { AgentView, CreateAgentRequest, CreatedAgent, PrincipalStatus } from '../types';

const STATUS_COLOR: Record<PrincipalStatus, string> = {
  ACTIVE: 'green',
  LOCKED: 'orange',
  DISABLED: 'default',
};

export function AgentsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.agents.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [credential, setCredential] = useState<CreatedAgent | null>(null);
  const [allowlistTarget, setAllowlistTarget] = useState<AgentView | null>(null);
  const [createForm] = Form.useForm();
  const [allowlistForm] = Form.useForm<{ tools: string[] }>();

  const query = useQuery({ queryKey: ['admin', 'agents'], queryFn: listAgents });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'agents'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (payload: CreateAgentRequest) => createAgent(payload),
    onSuccess: (data) => {
      setCreateOpen(false);
      createForm.resetFields();
      setCredential(data);
      void invalidate();
    },
    onError,
  });

  const enableMutation = useMutation({
    mutationFn: (agentId: string) => enableAgent(agentId),
    onSuccess: () => {
      message.success(t('admin.users.enabled'));
      void invalidate();
    },
    onError,
  });

  const disableMutation = useMutation({
    mutationFn: (agentId: string) => disableAgent(agentId),
    onSuccess: () => {
      message.success(t('admin.users.disabled'));
      void invalidate();
    },
    onError,
  });

  const allowlistMutation = useMutation({
    mutationFn: (vars: { agentId: string; tools: string[] }) =>
      updateAgentToolAllowlist(vars.agentId, { tools: vars.tools }),
    onSuccess: () => {
      message.success(t('admin.agents.allowlistUpdated'));
      setAllowlistTarget(null);
      void invalidate();
    },
    onError,
  });

  const columns: ColumnsType<AgentView> = [
    {
      title: 'Agent',
      key: 'agent',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.displayName}</Typography.Text>
          <Typography.Text type="secondary" code>
            {record.agentId}
          </Typography.Text>
        </Space>
      ),
    },
    { title: t('admin.agents.agentType'), dataIndex: 'agentType', key: 'agentType' },
    { title: t('admin.agents.vendor'), dataIndex: 'vendor', key: 'vendor', render: (v: string) => v || '-' },
    { title: t('admin.agents.maxSensitivityLevel'), dataIndex: 'maxSensitivityLevel', key: 'maxSensitivityLevel' },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: PrincipalStatus) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: t('admin.agents.toolAllowlist'),
      dataIndex: 'toolAllowlist',
      key: 'toolAllowlist',
      render: (tools: string[]) => (
        <Space size={[0, 4]} wrap>
          {tools.length ? tools.map((tool) => <Tag key={tool}>{tool}</Tag>) : '-'}
        </Space>
      ),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Space size="small" wrap>
          {record.status === 'DISABLED' ? (
            <Button type="link" size="small" onClick={() => enableMutation.mutate(record.agentId)}>
              {t('common.enable')}
            </Button>
          ) : (
            <Popconfirm
              title={t('admin.agents.confirmDisable')}
              onConfirm={() => disableMutation.mutate(record.agentId)}
            >
              <Button type="link" size="small" danger>
                {t('common.disable')}
              </Button>
            </Popconfirm>
          )}
          <Button
            type="link"
            size="small"
            onClick={() => {
              setAllowlistTarget(record);
              allowlistForm.setFieldsValue({ tools: record.toolAllowlist });
            }}
          >
            {t('admin.agents.allowlist')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.agents.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            {t('admin.agents.registerAgent')}
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<AgentView> rowKey="agentId" columns={columns} dataSource={query.data ?? []} />
      </QueryBoundary>

      <Modal
        title={t('admin.agents.registerAgent')}
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
          initialValues={{ maxSensitivityLevel: 0, scopes: [] }}
          onFinish={(values) => createMutation.mutate(values as CreateAgentRequest)}
        >
          <Form.Item name="displayName" label={t('profile.displayName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="agentType" label={t('common.type')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.agents.agentTypePlaceholder')} />
          </Form.Item>
          <Form.Item name="vendor" label={t('admin.agents.vendor')}>
            <Input />
          </Form.Item>
          <Form.Item name="maxSensitivityLevel" label={t('admin.agents.maxSensitivityLevel')} rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="scopes" label={t('admin.agents.grantScopes')}>
            <Select mode="tags" placeholder={t('admin.agents.scopesPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('admin.agents.credentialTitle')}
        open={credential !== null}
        onCancel={() => setCredential(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setCredential(null)}>
            {t('admin.agents.credentialSaved')}
          </Button>,
        ]}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          message={t('admin.agents.credentialWarning')}
          description={t('admin.agents.credentialDesc')}
          style={{ marginBottom: 12 }}
        />
        <Typography.Paragraph copyable code>
          {credential?.credential}
        </Typography.Paragraph>
      </Modal>

      <Modal
        title={t('admin.agents.editAllowlist')}
        open={allowlistTarget !== null}
        onCancel={() => setAllowlistTarget(null)}
        onOk={() => allowlistForm.submit()}
        confirmLoading={allowlistMutation.isPending}
        destroyOnClose
      >
        <Form
          form={allowlistForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => {
            if (!allowlistTarget) {
              return;
            }
            allowlistMutation.mutate({ agentId: allowlistTarget.agentId, tools: values.tools ?? [] });
          }}
        >
          <Form.Item name="tools" label={t('admin.agents.allowedTools')}>
            <Select mode="tags" placeholder={t('admin.agents.toolPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
