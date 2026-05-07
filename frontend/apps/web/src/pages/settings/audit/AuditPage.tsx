/**
 * 功能：审计日志查询页（管理员）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Tag, Input, Select, DatePicker,
  Drawer, Descriptions, Timeline, Tabs, message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, NodeIndexOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { auditApi } from '@/services';

const { RangePicker } = DatePicker;

interface AuditLog {
  id: number;
  traceId: string;
  taskId?: number;
  operator: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  resourceName?: string;
  detail?: any;
  agentSessionId?: string;
  ipAddress?: string;
  userAgent?: string;
  status: string;
  durationMs?: number;
  createdAt: string;
}

const RESOURCE_TYPES = ['user', 'role', 'config', 'dict', 'task', 'ontology', 'entity', 'metric'];
const ACTIONS = ['create', 'update', 'delete', 'update_status', 'reset_password',
  'change_password', 'assign_roles', 'assign_permissions', 'import', 'export'];
const STATUSES = ['success', 'failure'];

export default function AuditPage() {
  const { t } = useTranslation(['audit', 'common']);
  const [tab, setTab] = useState<'logs' | 'trace'>('logs');

  // 列表页状态
  const [data, setData] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [operator, setOperator] = useState('');
  const [resourceType, setResourceType] = useState<string | undefined>();
  const [action, setAction] = useState<string | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [keyword, setKeyword] = useState('');
  const [timeRange, setTimeRange] = useState<any>(null);

  // 详情 drawer
  const [detailOpen, setDetailOpen] = useState(false);
  const [current, setCurrent] = useState<AuditLog | null>(null);

  // 链路视图
  const [traceInput, setTraceInput] = useState('');
  const [traceData, setTraceData] = useState<AuditLog[]>([]);
  const [traceLoading, setTraceLoading] = useState(false);

  const load = async (p = page, ps = pageSize) => {
    setLoading(true);
    try {
      const params: any = {
        page: p, size: ps,
        operator: operator || undefined,
        resourceType, action, status,
        keyword: keyword || undefined,
      };
      if (timeRange && timeRange[0] && timeRange[1]) {
        params.startTime = timeRange[0].toISOString();
        params.endTime = timeRange[1].toISOString();
      }
      const res: any = await auditApi.listLogs(params);
      setData(res.data?.items || []);
      setTotal(res.data?.total || 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { if (tab === 'logs') load(1, pageSize); setPage(1);
    /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [tab]);

  const onSearch = () => { setPage(1); load(1, pageSize); };

  const onReset = () => {
    setOperator('');
    setResourceType(undefined);
    setAction(undefined);
    setStatus(undefined);
    setKeyword('');
    setTimeRange(null);
    setPage(1);
    setTimeout(() => load(1, pageSize), 0);
  };

  const openDetail = async (log: AuditLog) => {
    setCurrent(log);
    setDetailOpen(true);
  };

  const openTraceByLog = (log: AuditLog) => {
    setTraceInput(log.traceId);
    setTab('trace');
    loadTrace(log.traceId);
  };

  const loadTrace = async (id: string) => {
    if (!id.trim()) return;
    setTraceLoading(true);
    try {
      const res: any = await auditApi.getTrace(id.trim());
      const list = res.data || [];
      setTraceData(list);
      if (list.length === 0) {
        message.info(t('trace.empty'));
      }
    } finally {
      setTraceLoading(false);
    }
  };

  const statusColor = (s: string) => s === 'success' ? 'green' : s === 'failure' ? 'red' : 'default';

  const columns: ColumnsType<AuditLog> = [
    {
      title: t('log.created_at'),
      dataIndex: 'createdAt',
      width: 170,
      fixed: 'left',
      render: (v: string) => new Date(v).toLocaleString(),
    },
    { title: t('log.operator'), dataIndex: 'operator', width: 120 },
    { title: t('log.action'), dataIndex: 'action', width: 140,
      render: (v: string) => <Tag>{v}</Tag> },
    { title: t('log.resource_type'), dataIndex: 'resourceType', width: 120 },
    { title: t('log.resource_id'), dataIndex: 'resourceId', width: 140,
      render: (v) => v || '-' },
    {
      title: t('log.status'),
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <Tag color={statusColor(v)}>{v}</Tag>,
    },
    {
      title: t('log.duration_ms'),
      dataIndex: 'durationMs',
      width: 100,
      render: (v) => v == null ? '-' : `${v} ms`,
    },
    { title: t('log.client_ip'), dataIndex: 'ipAddress', width: 140, render: (v) => v || '-' },
    {
      title: t('log.trace_id'),
      dataIndex: 'traceId',
      width: 170,
      render: (v: string) => <code style={{ fontSize: 12 }}>{v}</code>,
    },
    {
      title: t('common:action.operate', 'Operation'),
      width: 200,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => openDetail(r)}>{t('log.view')}</Button>
          <Button size="small" icon={<NodeIndexOutlined />} onClick={() => openTraceByLog(r)}>
            {t('log.view_trace')}
          </Button>
        </Space>
      ),
    },
  ];

  const formatDetail = (detail: any) => {
    if (detail == null) return '-';
    if (typeof detail === 'string') return detail || '-';
    try { return JSON.stringify(detail, null, 2); } catch { return String(detail); }
  };

  return (
    <div>
      <Tabs
        activeKey={tab}
        onChange={(k) => setTab(k as any)}
        items={[
          {
            key: 'logs',
            label: t('tabs.logs'),
            children: (
              <Card
                title={t('title')}
                extra={
                  <Space wrap>
                    <Input
                      allowClear
                      placeholder={t('filter.operator')}
                      value={operator}
                      onChange={(e) => setOperator(e.target.value)}
                      onPressEnter={onSearch}
                      style={{ width: 160 }}
                    />
                    <Select
                      allowClear
                      placeholder={t('filter.resource_type')}
                      value={resourceType}
                      onChange={setResourceType}
                      style={{ width: 140 }}
                      options={RESOURCE_TYPES.map(v => ({ value: v, label: v }))}
                    />
                    <Select
                      allowClear
                      placeholder={t('filter.action')}
                      value={action}
                      onChange={setAction}
                      style={{ width: 160 }}
                      options={ACTIONS.map(v => ({ value: v, label: v }))}
                    />
                    <Select
                      allowClear
                      placeholder={t('filter.status')}
                      value={status}
                      onChange={setStatus}
                      style={{ width: 120 }}
                      options={STATUSES.map(v => ({ value: v, label: v }))}
                    />
                    <RangePicker
                      showTime
                      value={timeRange}
                      onChange={setTimeRange}
                    />
                    <Input
                      allowClear
                      placeholder={t('filter.keyword')}
                      value={keyword}
                      onChange={(e) => setKeyword(e.target.value)}
                      onPressEnter={onSearch}
                      style={{ width: 200 }}
                    />
                    <Button onClick={onReset}>{t('common:action.filter', 'Reset')}</Button>
                    <Button type="primary" icon={<ReloadOutlined />} onClick={onSearch}>
                      {t('common:action.search', 'Search')}
                    </Button>
                  </Space>
                }
              >
                <Table<AuditLog>
                  rowKey="id"
                  loading={loading}
                  dataSource={data}
                  columns={columns}
                  scroll={{ x: 1500 }}
                  pagination={{
                    current: page,
                    pageSize,
                    total,
                    showSizeChanger: true,
                    onChange: (p, ps) => { setPage(p); setPageSize(ps); load(p, ps); },
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'trace',
            label: t('tabs.trace'),
            children: (
              <Card title={t('trace.title')}
                extra={
                  <Space>
                    <Input
                      allowClear
                      placeholder={t('trace.input_placeholder')}
                      value={traceInput}
                      onChange={(e) => setTraceInput(e.target.value)}
                      onPressEnter={() => loadTrace(traceInput)}
                      style={{ width: 320 }}
                    />
                    <Button type="primary" icon={<NodeIndexOutlined />}
                      onClick={() => loadTrace(traceInput)} loading={traceLoading}>
                      {t('common:action.search', 'Search')}
                    </Button>
                  </Space>
                }
              >
                {traceData.length === 0 ? (
                  <div style={{ color: 'var(--color-text-secondary)', padding: 40, textAlign: 'center' }}>
                    {t('trace.empty')}
                  </div>
                ) : (
                  <Timeline
                    mode="left"
                    items={traceData.map((log) => ({
                      color: statusColor(log.status),
                      label: new Date(log.createdAt).toLocaleString(),
                      children: (
                        <div>
                          <Space wrap>
                            <Tag>{log.action}</Tag>
                            <Tag color="blue">{log.resourceType}</Tag>
                            {log.resourceId && <code>{log.resourceId}</code>}
                            <Tag color={statusColor(log.status)}>{log.status}</Tag>
                            {log.durationMs != null && <span>{log.durationMs} ms</span>}
                          </Space>
                          <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', marginTop: 4 }}>
                            {t('log.operator')}: {log.operator}
                            {log.ipAddress && <>　IP: {log.ipAddress}</>}
                          </div>
                          {log.detail != null && (
                            <pre style={{
                              marginTop: 6,
                              fontSize: 12,
                              background: 'var(--color-bg-subtle)',
                              padding: 8,
                              borderRadius: 4,
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-all',
                            }}>{formatDetail(log.detail)}</pre>
                          )}
                        </div>
                      ),
                    }))}
                  />
                )}
              </Card>
            ),
          },
        ]}
      />

      <Drawer
        title={t('log.view')}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={720}
        extra={current && (
          <Button icon={<NodeIndexOutlined />} onClick={() => {
            setDetailOpen(false);
            openTraceByLog(current);
          }}>
            {t('log.view_trace')}
          </Button>
        )}
      >
        {current && (
          <>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label={t('log.id')}>{current.id}</Descriptions.Item>
              <Descriptions.Item label={t('log.created_at')}>
                {new Date(current.createdAt).toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label={t('log.operator')}>{current.operator}</Descriptions.Item>
              <Descriptions.Item label={t('log.status')}>
                <Tag color={statusColor(current.status)}>{current.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('log.action')}>{current.action}</Descriptions.Item>
              <Descriptions.Item label={t('log.duration_ms')}>
                {current.durationMs == null ? '-' : `${current.durationMs} ms`}
              </Descriptions.Item>
              <Descriptions.Item label={t('log.resource_type')}>{current.resourceType}</Descriptions.Item>
              <Descriptions.Item label={t('log.resource_id')}>{current.resourceId || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('log.trace_id')} span={2}>
                <code>{current.traceId}</code>
              </Descriptions.Item>
              <Descriptions.Item label={t('log.client_ip')}>{current.ipAddress || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('log.user_agent')}>{current.userAgent || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('log.detail')} span={2}>
                <pre style={{
                  margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all',
                  background: 'var(--color-bg-subtle)', padding: 8, borderRadius: 4,
                }}>{formatDetail(current.detail)}</pre>
              </Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Drawer>
    </div>
  );
}
