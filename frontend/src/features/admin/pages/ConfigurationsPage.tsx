/**
 * 功能: 配置管理页面。非敏感运行配置列表 + 编辑（乐观并发 expectedVersion）。
 *       Secret 键后端会拒绝，前端在错误时透传后端 message 提示。
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
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listConfigurations, updateConfiguration } from '../api';
import type { ConfigurationView } from '../types';

export function ConfigurationsPage() {
  useDocumentTitle('配置管理');
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState<ConfigurationView | null>(null);
  const [form] = Form.useForm<{ value: string; confirmation?: string }>();

  const query = useQuery({ queryKey: ['admin', 'configurations'], queryFn: listConfigurations });

  const updateMutation = useMutation({
    mutationFn: (vars: { configKey: string; value: unknown; expectedVersion: number; confirmation?: string }) =>
      updateConfiguration(vars.configKey, {
        value: vars.value,
        expectedVersion: vars.expectedVersion,
        confirmation: vars.confirmation || null,
      }),
    onSuccess: () => {
      message.success('配置已更新');
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'configurations'] });
    },
    onError: (error) => message.error(isApiError(error) ? error.message : '操作失败'),
  });

  const parseValue = (raw: string, valueType: ConfigurationView['valueType']): unknown => {
    if (valueType === 'JSON') {
      return JSON.parse(raw);
    }
    if (valueType === 'INTEGER' || valueType === 'LONG') {
      return Number(raw);
    }
    if (valueType === 'BOOLEAN') {
      return raw === 'true';
    }
    return raw;
  };

  const columns: ColumnsType<ConfigurationView> = [
    { title: '配置键', dataIndex: 'configKey', key: 'configKey' },
    {
      title: '类型',
      dataIndex: 'valueType',
      key: 'valueType',
      render: (type: string) => <Tag>{type}</Tag>,
    },
    {
      title: '当前值',
      dataIndex: 'value',
      key: 'value',
      render: (value: unknown) => (
        <Typography.Text code>
          {typeof value === 'object' ? JSON.stringify(value) : String(value)}
        </Typography.Text>
      ),
    },
    {
      title: '热更新',
      dataIndex: 'hotReloadable',
      key: 'hotReloadable',
      render: (value: boolean) => (value ? '是' : '否'),
    },
    { title: '版本', dataIndex: 'version', key: 'version' },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => {
            setEditing(record);
            const initial =
              record.valueType === 'JSON'
                ? JSON.stringify(record.value, null, 2)
                : String(record.value ?? '');
            form.setFieldsValue({ value: initial, confirmation: undefined });
          }}
        >
          编辑
        </Button>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          配置管理
        </Typography.Title>
        <Button onClick={() => query.refetch()}>刷新</Button>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<ConfigurationView>
          rowKey="configKey"
          columns={columns}
          dataSource={query.data ?? []}
        />
      </QueryBoundary>

      <Modal
        title={editing ? `编辑配置 · ${editing.configKey}` : '编辑配置'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        confirmLoading={updateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          onFinish={(values) => {
            if (!editing) {
              return;
            }
            try {
              const parsed = parseValue(values.value, editing.valueType);
              updateMutation.mutate({
                configKey: editing.configKey,
                value: parsed,
                expectedVersion: editing.version,
                confirmation: values.confirmation,
              });
            } catch {
              message.error('值格式无效，请检查（JSON 需为合法 JSON）');
            }
          }}
        >
          <Form.Item name="value" label={`值 (${editing?.valueType})`} rules={[{ required: true }]}>
            {editing?.valueType === 'JSON' ? (
              <Input.TextArea rows={6} />
            ) : (
              <Input />
            )}
          </Form.Item>
          <Form.Item
            name="confirmation"
            label="二次确认"
            extra="安全/发布策略配置按服务端要求提供确认值"
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
