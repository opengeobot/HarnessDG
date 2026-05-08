/**
 * 功能：数据接入管理页 - 数据源管理和接入任务管理（Tab切换）
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Input, Tabs, Modal, Form, message, Popconfirm, Tag, Select,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, ThunderboltOutlined, SyncOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { ingestionApi } from '@/services/ingestionApi';
import { DictSelect, DictTag } from '@/components/dict';

interface DataSource {
  id: number;
  name: string;
  type: string;
  host: string;
  database: string;
  status: string;
  createdAt: string;
}

interface IngestionTask {
  id: number;
  name: string;
  sourceId: number;
  sourceName: string;
  targetEntity: string;
  mode: string;
  schedule: string;
  status: string;
  lastSyncAt?: string;
}

export default function DataIngestionPage() {
  const { t } = useTranslation(['ingestion', 'common']);
  const [sources, setSources] = useState<DataSource[]>([]);
  const [tasks, setTasks] = useState<IngestionTask[]>([]);
  const [loadingSources, setLoadingSources] = useState(false);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [sourceModal, setSourceModal] = useState(false);
  const [taskModal, setTaskModal] = useState(false);
  const [editingSource, setEditingSource] = useState<DataSource | null>(null);
  const [editingTask, setEditingTask] = useState<IngestionTask | null>(null);
  const [sourceForm] = Form.useForm();
  const [taskForm] = Form.useForm();

  const loadSources = async () => {
    setLoadingSources(true);
    try {
      const res: any = await ingestionApi.listDataSources();
      setSources(res.data || []);
    } finally {
      setLoadingSources(false);
    }
  };

  const loadTasks = async () => {
    setLoadingTasks(true);
    try {
      const res: any = await ingestionApi.listTasks();
      setTasks(res.data || []);
    } finally {
      setLoadingTasks(false);
    }
  };

  useEffect(() => { loadSources(); loadTasks(); }, []);

  const handleSaveSource = async () => {
    const v = await sourceForm.validateFields();
    try {
      if (editingSource) {
        // update logic if needed
      } else {
        await ingestionApi.createDataSource(v);
      }
      message.success(t('common:action.save_success', '保存成功'));
      setSourceModal(false);
      loadSources();
    } catch {
      message.error(t('common:action.save_fail', '保存失败'));
    }
  };

  const handleSaveTask = async () => {
    const v = await taskForm.validateFields();
    try {
      if (editingTask) {
        // update logic
      } else {
        await ingestionApi.createTask(v);
      }
      message.success(t('common:action.save_success', '保存成功'));
      setTaskModal(false);
      loadTasks();
    } catch {
      message.error(t('common:action.save_fail', '保存失败'));
    }
  };

  const handleTestConnection = async (id: number) => {
    try {
      await ingestionApi.testConnection(id);
      message.success(t('ingestion.test_success', '连通性测试成功'));
    } catch {
      message.error(t('ingestion.test_fail', '连通性测试失败'));
    }
  };

  const handleSync = async (id: number) => {
    try {
      await ingestionApi.syncTask(id);
      message.success(t('ingestion.sync_started', '同步已启动'));
    } catch {
      message.error(t('ingestion.sync_failed', '同步启动失败'));
    }
  };

  const openCreateSource = () => {
    setEditingSource(null);
    sourceForm.resetFields();
    setSourceModal(true);
  };

  const openCreateTask = () => {
    setEditingTask(null);
    taskForm.resetFields();
    setTaskModal(true);
  };

  const sourceCols: ColumnsType<DataSource> = [
    { title: t('ingestion.source_name'), dataIndex: 'name' },
    {
      title: t('ingestion.source_type'),
      dataIndex: 'type',
      width: 120,
      render: (v: string) => <DictTag groupCode="datasource_type" code={v} />,
    },
    { title: t('ingestion.host'), dataIndex: 'host', width: 180 },
    { title: t('ingestion.database'), dataIndex: 'database', width: 140 },
    {
      title: t('ingestion.status'),
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <DictTag groupCode="datasource_status" code={v} />,
    },
    { title: t('ingestion.created_at'), dataIndex: 'createdAt', width: 180 },
    {
      title: t('common:action.title', '操作'),
      fixed: 'right',
      width: 160,
      render: (_, r) => (
        <Space>
          <Button size="small" icon={<ThunderboltOutlined />} onClick={() => handleTestConnection(r.id)}>
            {t('ingestion.test_connection', '测试')}
          </Button>
        </Space>
      ),
    },
  ];

  const taskCols: ColumnsType<IngestionTask> = [
    { title: t('ingestion.task_name'), dataIndex: 'name' },
    { title: t('ingestion.source'), dataIndex: 'sourceName', width: 140 },
    { title: t('ingestion.target_entity'), dataIndex: 'targetEntity', width: 140 },
    {
      title: t('ingestion.mode'),
      dataIndex: 'mode',
      width: 100,
      render: (v: string) => <DictTag groupCode="sync_mode" code={v} />,
    },
    { title: t('ingestion.schedule'), dataIndex: 'schedule', width: 140 },
    {
      title: t('ingestion.status'),
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <DictTag groupCode="task_status" code={v} />,
    },
    { title: t('ingestion.last_sync'), dataIndex: 'lastSyncAt', width: 180 },
    {
      title: t('common:action.title', '操作'),
      fixed: 'right',
      width: 120,
      render: (_, r) => (
        <Button size="small" icon={<SyncOutlined />} onClick={() => handleSync(r.id)}>
          {t('ingestion.sync', '同步')}
        </Button>
      ),
    },
  ];

  const tabItems = [
    {
      key: 'sources',
      label: t('ingestion.tab_sources', '数据源'),
      children: (
        <Card
          title={t('ingestion.data_sources', '数据源管理')}
          extra={
            <Space>
              <Button icon={<ReloadOutlined />} onClick={loadSources} />
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateSource}>
                {t('ingestion.create_source', '新建数据源')}
              </Button>
            </Space>
          }
        >
          <Table<DataSource> rowKey="id" columns={sourceCols} dataSource={sources} loading={loadingSources} scroll={{ x: 1000 }} />
        </Card>
      ),
    },
    {
      key: 'tasks',
      label: t('ingestion.tab_tasks', '接入任务'),
      children: (
        <Card
          title={t('ingestion.ingestion_tasks', '接入任务管理')}
          extra={
            <Space>
              <Button icon={<ReloadOutlined />} onClick={loadTasks} />
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateTask}>
                {t('ingestion.create_task', '新建任务')}
              </Button>
            </Space>
          }
        >
          <Table<IngestionTask> rowKey="id" columns={taskCols} dataSource={tasks} loading={loadingTasks} scroll={{ x: 1200 }} />
        </Card>
      ),
    },
  ];

  return (
    <>
      <Tabs defaultActiveKey="sources" items={tabItems} />

      <Modal
        open={sourceModal}
        title={t('ingestion.create_source', '新建数据源')}
        onCancel={() => setSourceModal(false)}
        onOk={handleSaveSource}
        width={560}
      >
        <Form form={sourceForm} layout="vertical">
          <Form.Item name="name" label={t('ingestion.source_name')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label={t('ingestion.source_type')} rules={[{ required: true }]}>
            <DictSelect groupCode="datasource_type" />
          </Form.Item>
          <Form.Item name="host" label={t('ingestion.host')} rules={[{ required: true }]}>
            <Input placeholder="localhost:3306" />
          </Form.Item>
          <Form.Item name="database" label={t('ingestion.database')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="username" label={t('ingestion.username')}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label={t('ingestion.password')}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={taskModal}
        title={t('ingestion.create_task', '新建接入任务')}
        onCancel={() => setTaskModal(false)}
        onOk={handleSaveTask}
        width={560}
      >
        <Form form={taskForm} layout="vertical">
          <Form.Item name="name" label={t('ingestion.task_name')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="sourceId" label={t('ingestion.source')} rules={[{ required: true }]}>
            <Select options={sources.map(s => ({ value: s.id, label: s.name }))} />
          </Form.Item>
          <Form.Item name="targetEntity" label={t('ingestion.target_entity')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="mode" label={t('ingestion.mode')} rules={[{ required: true }]}>
            <DictSelect groupCode="sync_mode" />
          </Form.Item>
          <Form.Item name="schedule" label={t('ingestion.schedule')}>
            <Input placeholder="0 0 * * *" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
