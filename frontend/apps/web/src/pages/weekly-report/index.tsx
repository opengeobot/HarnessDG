/**
 * 功能：周报列表页 - 展示周报列表，支持新建和查看
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Modal, Form, DatePicker, Select, message, Drawer, Input,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { reportApi } from '@/services/reportApi';
import ReportView from './ReportView';

const { RangePicker } = DatePicker;

interface WeeklyReport {
  id: number;
  title: string;
  reportType: string;
  startDate: string;
  endDate: string;
  creator: string;
  createdAt: string;
  status: string;
}

export default function WeeklyReportList() {
  const { t } = useTranslation(['report', 'common']);
  const [data, setData] = useState<WeeklyReport[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModal, setCreateModal] = useState(false);
  const [detailDrawer, setDetailDrawer] = useState(false);
  const [selectedReport, setSelectedReport] = useState<WeeklyReport | null>(null);
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const res: any = await reportApi.listReports();
      setData(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  const handleCreate = async () => {
    const v = await form.validateFields();
    try {
      const payload = {
        ...v,
        startDate: v.dateRange?.[0]?.format('YYYY-MM-DD'),
        endDate: v.dateRange?.[1]?.format('YYYY-MM-DD'),
      };
      await reportApi.createReport(payload);
      message.success(t('report.create_success', '创建成功'));
      setCreateModal(false);
      loadData();
    } catch {
      message.error(t('report.create_fail', '创建失败'));
    }
  };

  const viewDetail = (record: WeeklyReport) => {
    setSelectedReport(record);
    setDetailDrawer(true);
  };

  const columns: ColumnsType<WeeklyReport> = [
    { title: t('report.title'), dataIndex: 'title', ellipsis: true },
    {
      title: t('report.report_type'),
      dataIndex: 'reportType',
      width: 120,
      render: (v: string) => v,
    },
    { title: t('report.date_range'), dataIndex: 'startDate', width: 220,
      render: (_: any, r: WeeklyReport) => `${r.startDate} ~ ${r.endDate}` },
    { title: t('report.creator'), dataIndex: 'creator', width: 120 },
    { title: t('report.created_at'), dataIndex: 'createdAt', width: 180 },
    {
      title: t('common:action.title', '操作'),
      fixed: 'right',
      width: 100,
      render: (_, r) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => viewDetail(r)}>
          {t('common:action.view', '查看')}
        </Button>
      ),
    },
  ];

  return (
    <Card
      title={t('report.list', '周报列表')}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadData} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModal(true)}>
            {t('report.create', '新建周报')}
          </Button>
        </Space>
      }
    >
      <Table<WeeklyReport> rowKey="id" columns={columns} dataSource={data} loading={loading} />

      <Modal
        open={createModal}
        title={t('report.create', '新建周报')}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreate}
        width={480}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label={t('report.title')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="reportType" label={t('report.report_type')} rules={[{ required: true }]}>
            <Select options={[
              { value: 'weekly', label: t('report.type_weekly', '周报') },
              { value: 'monthly', label: t('report.type_monthly', '月报') },
              { value: 'quarterly', label: t('report.type_quarterly', '季报') },
            ]} />
          </Form.Item>
          <Form.Item name="dateRange" label={t('report.date_range')} rules={[{ required: true }]}>
            <RangePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={detailDrawer}
        title={selectedReport?.title || ''}
        onClose={() => setDetailDrawer(false)}
        width={720}
      >
        {selectedReport && <ReportView report={selectedReport} />}
      </Drawer>
    </Card>
  );
}
