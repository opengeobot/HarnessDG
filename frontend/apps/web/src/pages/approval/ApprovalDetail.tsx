/**
 * 功能：审批详情页 - 展示审批实例信息和步骤进度
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Card, Descriptions, Steps, Button, Space, Modal, Form, Input, message, Tag,
} from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { approvalApi } from '@/services/approvalApi';
import { DictTag } from '@/components/dict';

interface ApprovalStep {
  id: number;
  stepName: string;
  approver: string;
  status: string;
  comment?: string;
  completedAt?: string;
}

interface ApprovalDetail {
  id: number;
  title: string;
  templateName: string;
  initiator: string;
  status: string;
  reason?: string;
  createdAt: string;
  steps: ApprovalStep[];
}

export default function ApprovalDetail() {
  const { t } = useTranslation(['approval', 'common']);
  const { id } = useParams<{ id: string }>();
  const [detail, setDetail] = useState<ApprovalDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [rejectModal, setRejectModal] = useState(false);
  const [rejectForm] = Form.useForm();
  const [currentStepId, setCurrentStepId] = useState<number | null>(null);

  const loadDetail = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const res: any = await approvalApi.getInstance(Number(id));
      setDetail(res.data);
      const pendingStep = res.data?.steps?.find((s: ApprovalStep) => s.status === 'pending');
      setCurrentStepId(pendingStep?.id || null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadDetail(); }, [id]);

  const handleApprove = async () => {
    if (!id || !currentStepId) return;
    try {
      await approvalApi.approveStep(Number(id), currentStepId, {});
      message.success(t('approval.approve_success', '审批通过'));
      loadDetail();
    } catch {
      message.error(t('approval.approve_fail', '审批失败'));
    }
  };

  const handleReject = async () => {
    if (!id || !currentStepId) return;
    const comment = rejectForm.getFieldValue('comment') || '';
    try {
      await approvalApi.rejectStep(Number(id), currentStepId, { comment });
      message.success(t('approval.reject_success', '已驳回'));
      setRejectModal(false);
      loadDetail();
    } catch {
      message.error(t('approval.reject_fail', '驳回失败'));
    }
  };

  if (!detail) return <Card loading />;

  const statusIcon = (status: string) => {
    if (status === 'approved') return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
    if (status === 'rejected') return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
    return <ClockCircleOutlined style={{ color: '#faad14' }} />;
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card title={t('approval.detail', '审批详情')} extra={<Button icon={<ReloadOutlined />} onClick={loadDetail} />}>
        <Descriptions column={2} bordered>
          <Descriptions.Item label={t('approval.title_label')}>{detail.title}</Descriptions.Item>
          <Descriptions.Item label={t('approval.template')}>{detail.templateName}</Descriptions.Item>
          <Descriptions.Item label={t('approval.initiator')}>{detail.initiator}</Descriptions.Item>
          <Descriptions.Item label={t('approval.status')}>
            <DictTag groupCode="approval_status" code={detail.status} />
          </Descriptions.Item>
          <Descriptions.Item label={t('approval.reason')} span={2}>{detail.reason || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('approval.created_at')}>{detail.createdAt}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={t('approval.steps', '审批步骤')}>
        <Steps direction="vertical" current={detail.steps.findIndex(s => s.status === 'pending')}>
          {detail.steps.map((step) => (
            <Steps.Step
              key={step.id}
              title={step.stepName}
              description={
                <div>
                  <div>{t('approval.approver', '审批人')}: {step.approver}</div>
                  <div>{statusIcon(step.status)} <Tag color={
                    step.status === 'approved' ? 'success' :
                    step.status === 'rejected' ? 'error' : 'warning'
                  }>{step.status}</Tag></div>
                  {step.comment && <div style={{ color: '#888', marginTop: 4 }}>{t('approval.comment')}: {step.comment}</div>}
                  {step.completedAt && <div style={{ color: '#aaa', fontSize: 12 }}>{step.completedAt}</div>}
                </div>
              }
            />
          ))}
        </Steps>

        {currentStepId && detail.status === 'pending' && (
          <Space style={{ marginTop: 24 }}>
            <Button type="primary" icon={<CheckCircleOutlined />} onClick={handleApprove}>
              {t('approval.approve', '通过')}
            </Button>
            <Button danger icon={<CloseCircleOutlined />} onClick={() => setRejectModal(true)}>
              {t('approval.reject', '驳回')}
            </Button>
          </Space>
        )}
      </Card>

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
    </Space>
  );
}
