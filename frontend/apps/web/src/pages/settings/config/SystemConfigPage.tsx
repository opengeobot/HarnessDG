/**
 * 功能：系统配置管理页（管理员）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Tag, Modal, Form, Input, InputNumber,
  Select, Switch, message, Popconfirm, Drawer, Descriptions, Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, HistoryOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { configApi } from '@/services';

interface ConfigItem {
  id: number;
  configKey: string;
  configValue?: string;
  valueType: string;
  category: string;
  description?: Record<string, string>;
  isEncrypted?: boolean;
  isReadonly?: boolean;
  environment: string;
  updatedBy?: string;
  updatedAt?: string;
}

interface HistoryItem {
  id: number;
  configKey: string;
  oldValue?: string;
  newValue?: string;
  environment: string;
  changeType: string;
  changedBy: string;
  changedAt: string;
  comment?: string;
}

const CATEGORY_OPTIONS = ['agent', 'security', 'feature', 'system', 'schedule', 'custom'];
const ENV_OPTIONS = ['all', 'dev', 'test', 'prod'];
const VALUE_TYPES = ['string', 'number', 'boolean', 'json'];

export default function SystemConfigPage() {
  const { t, i18n } = useTranslation(['config', 'common']);
  const [data, setData] = useState<ConfigItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<string | undefined>();
  const [environment, setEnvironment] = useState<string | undefined>();

  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [current, setCurrent] = useState<ConfigItem | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const locale = i18n.language || 'zh_CN';

  const localeLabel = (map?: Record<string, string>) =>
    map?.[locale] || map?.['zh_CN'] || map?.['en_US'] || '';

  const load = async (p = page, ps = pageSize) => {
    setLoading(true);
    try {
      const res: any = await configApi.listConfigs({
        category, environment, keyword: keyword || undefined, page: p, size: ps,
      });
      setData(res.data?.items || []);
      setTotal(res.data?.total || 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(1, pageSize); setPage(1); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [category, environment]);

  const onSearch = () => { setPage(1); load(1, pageSize); };

  const onCreate = async () => {
    const v = await createForm.validateFields();
    await configApi.createConfig({
      configKey: v.configKey,
      configValue: v.configValue,
      valueType: v.valueType,
      category: v.category,
      description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
      isEncrypted: !!v.isEncrypted,
      isReadonly: !!v.isReadonly,
      environment: v.environment || 'all',
      comment: v.comment,
    });
    message.success(t('common:action.save_success', 'Saved'));
    setCreateOpen(false);
    createForm.resetFields();
    load();
  };

  const onEdit = async () => {
    if (!current) return;
    const v = await editForm.validateFields();
    await configApi.updateConfig(current.configKey, {
      configValue: v.configValue,
      valueType: v.valueType,
      category: v.category,
      description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
      isEncrypted: !!v.isEncrypted,
      isReadonly: !!v.isReadonly,
      environment: v.environment,
      comment: v.comment,
    });
    message.success(t('common:action.save_success', 'Saved'));
    setEditOpen(false);
    load();
  };

  const onDelete = async (key: string) => {
    await configApi.deleteConfig(key);
    message.success(t('common:action.delete_success', 'Deleted'));
    load();
  };

  const openHistory = async (item: ConfigItem) => {
    setCurrent(item);
    const res: any = await configApi.getHistory(item.configKey);
    setHistory(res.data || []);
    setHistoryOpen(true);
  };

  const openEdit = (item: ConfigItem) => {
    setCurrent(item);
    editForm.setFieldsValue({
      configValue: item.configValue,
      valueType: item.valueType,
      category: item.category,
      environment: item.environment,
      descZh: item.description?.zh_CN,
      descEn: item.description?.en_US,
      isEncrypted: item.isEncrypted,
      isReadonly: item.isReadonly,
    });
    setEditOpen(true);
  };

  const renderValue = (value?: string, encrypted?: boolean) => {
    if (encrypted) return <Tag color="orange">●●●●●●</Tag>;
    if (value == null) return '-';
    if (value.length > 80) {
      return <Tooltip title={<pre style={{ maxWidth: 520, whiteSpace: 'pre-wrap' }}>{value}</pre>}>
        <span>{value.slice(0, 80)}…</span>
      </Tooltip>;
    }
    return <span style={{ fontFamily: 'monospace' }}>{value}</span>;
  };

  const columns: ColumnsType<ConfigItem> = [
    { title: t('config.key'), dataIndex: 'configKey', width: 260, fixed: 'left' },
    { title: t('config.value'), dataIndex: 'configValue', render: (v, r) => renderValue(v, r.isEncrypted) },
    {
      title: t('config.value_type'),
      dataIndex: 'valueType',
      width: 90,
      render: (v) => <Tag>{t(`config.value_type_options.${v}`, v) as string}</Tag>,
    },
    { title: t('config.category'), dataIndex: 'category', width: 110 },
    {
      title: t('config.environment'),
      dataIndex: 'environment',
      width: 90,
      render: (v) => v === 'all' ? <Tag>all</Tag> : <Tag color="blue">{t(`config.environment_options.${v}`, v) as string}</Tag>,
    },
    {
      title: t('config.description'),
      dataIndex: 'description',
      render: (d) => localeLabel(d) || '-',
    },
    {
      title: t('config.is_readonly'),
      dataIndex: 'isReadonly',
      width: 80,
      render: (b) => b ? <Tag color="gold">RO</Tag> : '-',
    },
    {
      title: t('config.is_encrypted'),
      dataIndex: 'isEncrypted',
      width: 80,
      render: (b) => b ? <Tag color="red">ENC</Tag> : '-',
    },
    { title: t('config.last_updated_by'), dataIndex: 'updatedBy', width: 120 },
    {
      title: t('config.last_updated_at'),
      dataIndex: 'updatedAt',
      width: 170,
      render: (v: string) => v ? new Date(v).toLocaleString() : '-',
    },
    {
      title: t('common:action.operate', 'Operation'),
      width: 220,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => openEdit(r)} disabled={r.isReadonly}>
            {t('config.edit')}
          </Button>
          <Button size="small" icon={<HistoryOutlined />} onClick={() => openHistory(r)}>
            {t('config.view_history')}
          </Button>
          {!r.isReadonly && (
            <Popconfirm title={t('config.delete_confirm')} onConfirm={() => onDelete(r.configKey)}>
              <Button size="small" danger>{t('common:action.delete', 'Delete')}</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        title={t('title')}
        extra={
          <Space>
            <Input
              allowClear
              placeholder={t('config.key')}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={onSearch}
              style={{ width: 240 }}
            />
            <Select
              allowClear
              placeholder={t('config.category')}
              value={category}
              onChange={setCategory}
              style={{ width: 140 }}
              options={CATEGORY_OPTIONS.map(v => ({ value: v, label: v }))}
            />
            <Select
              allowClear
              placeholder={t('config.environment')}
              value={environment}
              onChange={setEnvironment}
              style={{ width: 140 }}
              options={ENV_OPTIONS.map(v => ({
                value: v,
                label: v === 'all' ? 'all' : (t(`config.environment_options.${v}`, v) as string),
              }))}
            />
            <Button icon={<ReloadOutlined />} onClick={onSearch}>
              {t('common:action.refresh', 'Refresh')}
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => {
              createForm.resetFields();
              createForm.setFieldsValue({ valueType: 'string', environment: 'all', category: 'custom' });
              setCreateOpen(true);
            }}>
              {t('config.create')}
            </Button>
          </Space>
        }
      >
        <Table<ConfigItem>
          rowKey="id"
          loading={loading}
          dataSource={data}
          columns={columns}
          scroll={{ x: 1600 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => { setPage(p); setPageSize(ps); load(p, ps); },
          }}
        />
      </Card>

      <Modal
        title={t('config.create')}
        open={createOpen}
        onOk={onCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnClose
        width={640}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="configKey" label={t('config.key')} rules={[{ required: true }]}>
            <Input placeholder="module.feature.key" />
          </Form.Item>
          <Form.Item name="configValue" label={t('config.value')}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Form.Item name="valueType" label={t('config.value_type')} rules={[{ required: true }]}>
              <Select options={VALUE_TYPES.map(v => ({
                value: v,
                label: t(`config.value_type_options.${v}`, v) as string,
              }))} />
            </Form.Item>
            <Form.Item name="category" label={t('config.category')} rules={[{ required: true }]}>
              <Select options={CATEGORY_OPTIONS.map(v => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item name="environment" label={t('config.environment')}>
              <Select options={ENV_OPTIONS.map(v => ({ value: v, label: v }))} />
            </Form.Item>
          </div>
          <Form.Item name="descZh" label={t('config.description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('config.description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 24 }}>
            <Form.Item name="isReadonly" label={t('config.is_readonly')} valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="isEncrypted" label={t('config.is_encrypted')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
          <Form.Item name="comment" label={t('history.changed_at') + ' - Comment'}>
            <Input placeholder="变更备注（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={t('config.edit') + (current ? ` - ${current.configKey}` : '')}
        open={editOpen}
        onClose={() => setEditOpen(false)}
        width={640}
        extra={<Button type="primary" onClick={onEdit}>{t('common:action.save', 'Save')}</Button>}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="configValue" label={t('config.value')}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Form.Item name="valueType" label={t('config.value_type')}>
              <Select options={VALUE_TYPES.map(v => ({
                value: v,
                label: t(`config.value_type_options.${v}`, v) as string,
              }))} />
            </Form.Item>
            <Form.Item name="category" label={t('config.category')}>
              <Select options={CATEGORY_OPTIONS.map(v => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item name="environment" label={t('config.environment')}>
              <Select options={ENV_OPTIONS.map(v => ({ value: v, label: v }))} />
            </Form.Item>
          </div>
          <Form.Item name="descZh" label={t('config.description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('config.description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 24 }}>
            <Form.Item name="isReadonly" label={t('config.is_readonly')} valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="isEncrypted" label={t('config.is_encrypted')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
          <Form.Item name="comment" label="Comment">
            <Input placeholder="变更备注（可选）" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={t('history.title') + (current ? ` - ${current.configKey}` : '')}
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        width={900}
      >
        {current && (
          <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('config.key')}>{current.configKey}</Descriptions.Item>
            <Descriptions.Item label={t('config.environment')}>{current.environment}</Descriptions.Item>
            <Descriptions.Item label={t('config.category')}>{current.category}</Descriptions.Item>
            <Descriptions.Item label={t('config.value_type')}>{current.valueType}</Descriptions.Item>
          </Descriptions>
        )}
        <Table<HistoryItem>
          rowKey="id"
          dataSource={history}
          pagination={false}
          size="small"
          scroll={{ x: 800 }}
          columns={[
            { title: t('history.changed_at'), dataIndex: 'changedAt', width: 170,
              render: (v: string) => new Date(v).toLocaleString() },
            { title: 'Type', dataIndex: 'changeType', width: 90,
              render: (v: string) => <Tag color={v === 'create' ? 'green' : v === 'delete' ? 'red' : 'blue'}>{v}</Tag> },
            { title: t('history.changed_by'), dataIndex: 'changedBy', width: 120 },
            { title: t('history.old_value'), dataIndex: 'oldValue',
              render: (v) => <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{v ?? '-'}</pre> },
            { title: t('history.new_value'), dataIndex: 'newValue',
              render: (v) => <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{v ?? '-'}</pre> },
            { title: 'Comment', dataIndex: 'comment', width: 160 },
          ]}
        />
      </Drawer>
    </div>
  );
}
