/**
 * 功能: 配置管理页面。非敏感运行配置列表 + 编辑（乐观并发 expectedVersion）。
 *       Secret 键后端会拒绝，前端在错误时透传后端 message 提示。
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
  const { t } = useTranslation();
  useDocumentTitle(t('admin.configurations.title'));
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
      message.success(t('admin.configurations.configUpdated'));
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'configurations'] });
    },
    onError: (error) => message.error(isApiError(error) ? error.message : t('common.operationFailed')),
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
    { title: t('admin.configurations.configKey'), dataIndex: 'configKey', key: 'configKey' },
    {
      title: t('common.type'),
      dataIndex: 'valueType',
      key: 'valueType',
      render: (type: string) => <Tag>{type}</Tag>,
    },
    {
      title: t('admin.configurations.currentValue'),
      dataIndex: 'value',
      key: 'value',
      render: (value: unknown) => (
        <Typography.Text code>
          {typeof value === 'object' ? JSON.stringify(value) : String(value)}
        </Typography.Text>
      ),
    },
    {
      title: t('admin.configurations.hotReloadable'),
      dataIndex: 'hotReloadable',
      key: 'hotReloadable',
      render: (value: boolean) => (value ? t('common.yes') : t('common.no')),
    },
    { title: t('admin.configurations.version'), dataIndex: 'version', key: 'version' },
    {
      title: t('common.action'),
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
          {t('common.edit')}
        </Button>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.configurations.title')}
        </Typography.Title>
        <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
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
        title={editing ? t('admin.configurations.editConfigOf', { key: editing.configKey }) : t('admin.configurations.editConfig')}
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
              message.error(t('admin.configurations.invalidJson'));
            }
          }}
        >
          <Form.Item name="value" label={t('admin.configurations.valueLabel', { type: editing?.valueType })} rules={[{ required: true }]}>
            {editing?.valueType === 'JSON' ? (
              <Input.TextArea rows={6} />
            ) : (
              <Input />
            )}
          </Form.Item>
          <Form.Item
            name="confirmation"
            label={t('admin.configurations.confirmation')}
            extra={t('admin.configurations.confirmationExtra')}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
