/**
 * 功能：异常诊断页 - 选择任务/指标/实体运行诊断，展示根因分析和修复建议
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useState } from 'react';
import {
  Card, Form, Select, Button, Space, Spin, Alert, Descriptions, Tag, Typography, Input, message,
} from 'antd';
import { PlayCircleOutlined, ToolOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { diagnosisApi } from '@/services/diagnosisApi';

const { Title, Text, Paragraph } = Typography;

interface DiagnosisResult {
  id: number;
  targetId: number;
  targetType: string;
  status: string;
  rootCauses: Array<{ cause: string; confidence: number; description: string }>;
  suggestions: Array<{ suggestion: string; priority: string; autoRemediable: boolean }>;
  createdAt: string;
}

export default function DiagnosisPage() {
  const { t } = useTranslation(['diagnosis', 'common']);
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [remediating, setRemediating] = useState(false);
  const [result, setResult] = useState<DiagnosisResult | null>(null);

  const handleRun = async () => {
    const v = await form.validateFields();
    setLoading(true);
    try {
      const res: any = await diagnosisApi.runDiagnosis(v);
      setResult(res.data);
      message.success(t('diagnosis.run_success', '诊断完成'));
    } catch {
      message.error(t('diagnosis.run_fail', '诊断失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleRemediate = async () => {
    if (!result) return;
    setRemediating(true);
    try {
      await diagnosisApi.remediate(result.id);
      message.success(t('diagnosis.remediate_success', '修复已执行'));
      const res: any = await diagnosisApi.getDiagnosis(result.id);
      setResult(res.data);
    } catch {
      message.error(t('diagnosis.remediate_fail', '修复失败'));
    } finally {
      setRemediating(false);
    }
  };

  const getPriorityColor = (p: string) => {
    if (p === 'high') return 'red';
    if (p === 'medium') return 'orange';
    return 'blue';
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card title={t('diagnosis.title', '异常诊断')}
        extra={<Button icon={<ReloadOutlined />} onClick={() => { setResult(null); form.resetFields(); }}>
          {t('common:action.reset', '重置')}
        </Button>}
      >
        <Form form={form} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item name="targetType" label={t('diagnosis.target_type')} rules={[{ required: true }]} initialValue="task">
            <Select style={{ width: 140 }} options={[
              { value: 'task', label: t('diagnosis.type_task', '任务') },
              { value: 'metric', label: t('diagnosis.type_metric', '指标') },
              { value: 'entity', label: t('diagnosis.type_entity', '实体') },
            ]} />
          </Form.Item>
          <Form.Item name="targetId" label={t('diagnosis.target_id')} rules={[{ required: true }]}>
            <Input type="number" style={{ width: 140 }} placeholder="ID" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleRun} loading={loading}>
              {t('diagnosis.run', '运行诊断')}
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Spin spinning={loading}>
        {result && (
          <>
            <Card title={t('diagnosis.result', '诊断结果')}>
              <Descriptions column={3} bordered>
                <Descriptions.Item label={t('diagnosis.target_id')}>{result.targetId}</Descriptions.Item>
                <Descriptions.Item label={t('diagnosis.target_type')}>{result.targetType}</Descriptions.Item>
                <Descriptions.Item label={t('diagnosis.status')}>
                  <Tag color={result.status === 'completed' ? 'success' : 'processing'}>{result.status}</Tag>
                </Descriptions.Item>
              </Descriptions>
            </Card>

            {result.rootCauses && result.rootCauses.length > 0 && (
              <Card title={t('diagnosis.root_causes', '根因分析')}>
                {result.rootCauses.map((rc, idx) => (
                  <Alert
                    key={idx}
                    type={rc.confidence > 0.8 ? 'error' : rc.confidence > 0.5 ? 'warning' : 'info'}
                    message={`${rc.cause} (${(rc.confidence * 100).toFixed(0)}%)`}
                    description={rc.description}
                    style={{ marginBottom: 8 }}
                    showIcon
                  />
                ))}
              </Card>
            )}

            {result.suggestions && result.suggestions.length > 0 && (
              <Card title={t('diagnosis.suggestions', '修复建议')}
                extra={
                  result.suggestions.some(s => s.autoRemediable) && (
                    <Button type="primary" icon={<ToolOutlined />} onClick={handleRemediate} loading={remediating}>
                      {t('diagnosis.one_click_fix', '一键修复')}
                    </Button>
                  )
                }
              >
                {result.suggestions.map((s, idx) => (
                  <div key={idx} style={{ marginBottom: 12, padding: 12, background: '#fafafa', borderRadius: 4 }}>
                    <Space>
                      <Text strong>{s.suggestion}</Text>
                      <Tag color={getPriorityColor(s.priority)}>{s.priority}</Tag>
                      {s.autoRemediable && <Tag color="green">{t('diagnosis.auto_fixable', '可自动修复')}</Tag>}
                    </Space>
                  </div>
                ))}
              </Card>
            )}
          </>
        )}
      </Spin>
    </Space>
  );
}
