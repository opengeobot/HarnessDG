/**
 * 功能: 配置管理页面。非敏感运行配置列表 + 编辑（乐观并发 expectedVersion）。
 *       Secret 键后端会拒绝，前端在错误时透传后端 message 提示。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Flex,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listConfigurations, updateConfiguration } from '../api';
import type { ConfigurationView, ConfigValueType } from '../types';

/** 编辑弹窗内表单值：控件类型随 valueType 变化，JSON 以文本形态承载 */
type ConfigFormValues = { value?: string | number | boolean; confirmation?: string };

/** 将后端配置值转换为表单控件初始值 */
function toFormValue(valueType: ConfigValueType, value: unknown): string | number | boolean | undefined {
  if (valueType === 'JSON') {
    return JSON.stringify(value, null, 2);
  }
  if (valueType === 'INTEGER' || valueType === 'LONG') {
    const n = Number(value);
    return Number.isFinite(n) ? n : undefined;
  }
  if (valueType === 'BOOLEAN') {
    return typeof value === 'boolean' ? value : undefined;
  }
  return value == null ? undefined : String(value);
}

/** 将表单控件值规范化为提交给后端的类型化值 */
function toSubmitValue(valueType: ConfigValueType, raw: unknown): unknown {
  if (valueType === 'JSON') {
    return JSON.parse(String(raw));
  }
  if (valueType === 'INTEGER' || valueType === 'LONG') {
    return Number(raw);
  }
  if (valueType === 'BOOLEAN') {
    return raw === true;
  }
  return String(raw);
}

/** 按配置值类型渲染对应输入控件：可枚举类型一律使用下拉框 */
function renderValueEditor(valueType: ConfigValueType | undefined): ReactElement {
  if (valueType === 'BOOLEAN') {
    return (
      <Select
        options={[
          { value: true, label: 'true' },
          { value: false, label: 'false' },
        ]}
      />
    );
  }
  if (valueType === 'INTEGER' || valueType === 'LONG') {
    return <InputNumber style={{ width: '100%' }} precision={0} />;
  }
  if (valueType === 'JSON') {
    return <Input.TextArea rows={6} />;
  }
  return <Input />;
}

export function ConfigurationsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.configurations.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState<ConfigurationView | null>(null);
  const [form] = Form.useForm<ConfigFormValues>();

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
            form.setFieldsValue({
              value: toFormValue(record.valueType, record.value),
              confirmation: undefined,
            });
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
              const parsed = toSubmitValue(editing.valueType, values.value);
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
          <Form.Item
            name="value"
            label={t('admin.configurations.valueLabel', { type: editing?.valueType })}
            rules={[
              {
                // BOOLEAN 的 false 是合法值，内置 required 会误判为空，改用显式空值校验
                validator: (_, v) =>
                  v === undefined || v === null || v === ''
                    ? Promise.reject(new Error(t('admin.configurations.valueRequired')))
                    : Promise.resolve(),
              },
            ]}
          >
            {renderValueEditor(editing?.valueType)}
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
