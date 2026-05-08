/**
 * 功能：审批列表页 - 展示审批实例列表，支持过滤和操作
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Input, Select, Modal, Form, message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, EyeOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { approvalApi } from '@/services/approvalApi';
import { DictTag } from '@/components/dict';

interface ApprovalInstance {
  id: number;
  title: string;
  templateId: number;
  templateName: string;
  initiator: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export default function ApprovalList() {
  const { t } = useTranslation(['approval', 'common']);
  const navigate = useNavigate();
  const [data, setData] = useState<ApprovalInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterStatus, setFilterStatus] = useState<string>();
  const [filterInitiator, setFilterInitiator] = useState<string>();
  const [rejectModal, setRejectModal] = useState(false);
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectForm] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const params: any = {};
      if (filterStatus) params.status = filterStatus;
      if (filterInitiator) params.initiator = filterInitiator;
      const res: any = await approvalApi.listInstances(params);
      setData(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [filterStatus, filterInitiator]);

  const handleApprove = async (id: number) => {
    try {
      await approvalApi.approveStep(id, 1, { comment: '' });
      message.success(t('approval.approve_success', '审批通过'));
      loadData();
    } catch {
      message.error(t('approval.approve_fail', '审批失败'));
    }
  };

  const handleReject = async () => {
    if (!rejectId) return;
    const comment = rejectForm.getFieldValue('comment') || '';
    try {
      await approvalApi.rejectStep(rejectId, 1, { comment });
      message.success(t('approval.reject_success', '已驳回'));
      setRejectModal(false);
      loadData();
    } catch {
      message.error(t('approval.reject_fail', '驳回失败'));
    }
  };

  const openReject = (id: number) => {
    setRejectId(id);
    rejectForm.resetFields();
    setRejectModal(true);
  };

  const columns: ColumnsType<ApprovalInstance> = [
    { title: t('approval.title_label'), dataIndex: 'title', ellipsis: true },
    { title: t('approval.template'), dataIndex: 'templateName', width: 160 },
    { title: t('approval.initiator'), dataIndex: 'initiator', width: 120 },
    {
      title: t('approval.status'),
      dataIndex: 'status',
      width: 120,
      render: (v: string) => <DictTag groupCode="approval_status" code={v} />,
    },
    { title: t('approval.created_at'), dataIndex: 'createdAt', width: 180 },
    {
      title: t('common:action.title', '操作'),
      fixed: 'right',
      width: 200,
      render: (_, r) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/approval/${r.id}`)}>
            {t('common:action.view', '查看')}
          </Button>
          {r.status === 'pending' && (
            <>
              <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => handleApprove(r.id)}>
                {t('approval.approve', '通过')}
              </Button>
              <Button size="small" danger icon={<CloseOutlined />} onClick={() => openReject(r.id)}>
                {t('approval.reject', '驳回')}
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('approval.list')}
      extra={
        <Space>
          <Select allowClear placeholder={t('approval.status')} value={filterStatus}
            onChange={setFilterStatus} style={{ width: 140 }}
            options={[
              { value: 'pending', label: t('approval.pending', '待审批') },
              { value: 'approved', label: t('approval.approved', '已通过') },
              { value: 'rejected', label: t('approval.rejected', '已驳回') },
            ]}
          />
          <Input placeholder={t('approval.initiator')} value={filterInitiator}
            onChange={e => setFilterInitiator(e.target.value)}
            allowClear style={{ width: 140 }} />
          <Button icon={<ReloadOutlined />} onClick={loadData} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/approval/create')}>
            {t('approval.create', '发起审批')}
          </Button>
        </Space>
      }
    >
      <Table<ApprovalInstance>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1000 }}
      />

      <Modal
        open={rejectModal}
        title={t('approval.reject', '驳回')}
        onCancel={() => setRejectModal(false)}
        onOk={handleReject}
      >
        <Form form={rejectForm} layout="vertical">
          <Form.Item name="comment" label={t('approval.comment', '审批意见')}
            rules={[{ required: true, message: t('common:validation.required', '必填') }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
