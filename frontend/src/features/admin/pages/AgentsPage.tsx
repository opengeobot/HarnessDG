/**
 * 功能: Agent 管理页面。列表 + 注册（创建响应含一次性凭据）+ 启用/禁用 + Tool 白名单编辑。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
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
  useDocumentTitle('Agent 管理');
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
    message.error(isApiError(error) ? error.message : '操作失败');

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
      message.success('已启用');
      void invalidate();
    },
    onError,
  });

  const disableMutation = useMutation({
    mutationFn: (agentId: string) => disableAgent(agentId),
    onSuccess: () => {
      message.success('已禁用');
      void invalidate();
    },
    onError,
  });

  const allowlistMutation = useMutation({
    mutationFn: (vars: { agentId: string; tools: string[] }) =>
      updateAgentToolAllowlist(vars.agentId, { tools: vars.tools }),
    onSuccess: () => {
      message.success('白名单已更新');
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
    { title: '类型', dataIndex: 'agentType', key: 'agentType' },
    { title: '厂商', dataIndex: 'vendor', key: 'vendor', render: (v: string) => v || '-' },
    { title: '最大敏感级', dataIndex: 'maxSensitivityLevel', key: 'maxSensitivityLevel' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: PrincipalStatus) => <Tag color={STATUS_COLOR[status]}>{status}</Tag>,
    },
    {
      title: 'Tool 白名单',
      dataIndex: 'toolAllowlist',
      key: 'toolAllowlist',
      render: (tools: string[]) => (
        <Space size={[0, 4]} wrap>
          {tools.length ? tools.map((tool) => <Tag key={tool}>{tool}</Tag>) : '-'}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="small" wrap>
          {record.status === 'DISABLED' ? (
            <Button type="link" size="small" onClick={() => enableMutation.mutate(record.agentId)}>
              启用
            </Button>
          ) : (
            <Popconfirm
              title="确认禁用该 Agent？凭据与 Token 将被吊销。"
              onConfirm={() => disableMutation.mutate(record.agentId)}
            >
              <Button type="link" size="small" danger>
                禁用
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
            白名单
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Agent 管理
        </Typography.Title>
        <Space>
          <Button onClick={() => query.refetch()}>刷新</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            注册 Agent
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
        title="注册 Agent"
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
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="agentType" label="类型" rules={[{ required: true }]}>
            <Input placeholder="如 openclaw" />
          </Form.Item>
          <Form.Item name="vendor" label="厂商">
            <Input />
          </Form.Item>
          <Form.Item name="maxSensitivityLevel" label="最大敏感级" rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="scopes" label="授予 Scope">
            <Select mode="tags" placeholder="输入权限编码，如 asset:read" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="一次性 Agent 凭据"
        open={credential !== null}
        onCancel={() => setCredential(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setCredential(null)}>
            我已妥善保存
          </Button>,
        ]}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          message="凭据仅显示一次"
          description="请立即复制并安全保存；关闭后无法再次查看。"
          style={{ marginBottom: 12 }}
        />
        <Typography.Paragraph copyable code>
          {credential?.credential}
        </Typography.Paragraph>
      </Modal>

      <Modal
        title="编辑 Tool 白名单"
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
          <Form.Item name="tools" label="允许调用的 MCP Tool">
            <Select mode="tags" placeholder="输入 tool 名称，如 asset_search" />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
