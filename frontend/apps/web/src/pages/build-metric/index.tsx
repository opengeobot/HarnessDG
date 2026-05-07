import { useState } from 'react';
import { Steps, Card, Form, Input, Select, Button, Space, message, Result } from 'antd';
import { useTranslation } from 'react-i18next';
import { ontologyApi, taskApi } from '@/services';

const { Step } = Steps;
const { TextArea } = Input;

interface MetricFormData {
  entityId?: number;
  code: string;
  name: { zh_CN: string; en_US: string };
  description: { zh_CN: string; en_US: string };
  metricType: string;
  aggMethod: string;
  expression: string;
  unit: string;
}

export default function BuildMetric() {
  const { t } = useTranslation('task');
  const [current, setCurrent] = useState(0);
  const [form] = Form.useForm();
  const [entities, setEntities] = useState<any[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [taskResult, setTaskResult] = useState<any>(null);

  const loadEntities = async () => {
    try {
      const res = await ontologyApi.listEntities({ status: 'active', size: 100 });
      setEntities(res.data?.items || []);
    } catch {
      // fallback
    }
  };

  const steps = [
    { title: t('buildMetric.step1', '选择实体') },
    { title: t('buildMetric.step2', '定义指标') },
    { title: t('buildMetric.step3', '确认提交') },
  ];

  const handleNext = async () => {
    try {
      await form.validateFields();
      if (current === 0 && entities.length === 0) {
        await loadEntities();
      }
      setCurrent(current + 1);
    } catch {
      // validation error
    }
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const values = form.getFieldsValue(true) as MetricFormData;

      // 创建指标
      await ontologyApi.createMetric({
        code: values.code,
        name: values.name,
        description: values.description,
        entityId: values.entityId,
        metricType: values.metricType,
        aggMethod: values.aggMethod,
        expression: values.expression,
        unit: values.unit,
      });

      // 创建任务记录
      const taskRes = await taskApi.createTask({
        title: `构建指标: ${values.code}`,
        taskType: 'build_metric',
        inputPayload: values,
      });

      setTaskResult(taskRes.data);
      setCurrent(3);
      message.success(t('buildMetric.success', '指标创建成功'));
    } catch (err: any) {
      message.error(err?.message || '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 24, maxWidth: 800, margin: '0 auto' }}>
      <Card title={t('buildMetric.title', '构建指标向导')}>
        <Steps current={current} style={{ marginBottom: 32 }}>
          {steps.map((s) => (
            <Step key={s.title} title={s.title} />
          ))}
        </Steps>

        <Form form={form} layout="vertical">
          {current === 0 && (
            <>
              <Form.Item name="entityId" label={t('buildMetric.entity', '关联实体')} rules={[{ required: true }]}>
                <Select
                  placeholder={t('buildMetric.selectEntity', '请选择实体')}
                  options={entities.map((e) => ({ label: e.code, value: e.id }))}
                  onFocus={loadEntities}
                  showSearch
                  filterOption={(input, option) =>
                    (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                  }
                />
              </Form.Item>
            </>
          )}

          {current === 1 && (
            <>
              <Form.Item name="code" label={t('buildMetric.code', '指标编码')} rules={[{ required: true }]}>
                <Input placeholder="e.g. revenue_total" />
              </Form.Item>
              <Form.Item name={['name', 'zh_CN']} label={t('buildMetric.nameZh', '指标名称(中文)')} rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item name={['name', 'en_US']} label={t('buildMetric.nameEn', '指标名称(英文)')}>
                <Input />
              </Form.Item>
              <Form.Item name="metricType" label={t('buildMetric.metricType', '指标类型')} rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: '原子指标', value: 'atomic' },
                    { label: '派生指标', value: 'derived' },
                    { label: '复合指标', value: 'composite' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="aggMethod" label={t('buildMetric.aggMethod', '聚合方式')} rules={[{ required: true }]}>
                <Select
                  options={[
                    { label: 'SUM', value: 'sum' },
                    { label: 'COUNT', value: 'count' },
                    { label: 'AVG', value: 'avg' },
                    { label: 'MAX', value: 'max' },
                    { label: 'MIN', value: 'min' },
                    { label: 'COUNT DISTINCT', value: 'count_distinct' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="expression" label={t('buildMetric.expression', '计算表达式')}>
                <TextArea rows={3} placeholder="e.g. SUM(order_amount)" />
              </Form.Item>
              <Form.Item name="unit" label={t('buildMetric.unit', '单位')}>
                <Input placeholder="e.g. 元, 个, %" />
              </Form.Item>
            </>
          )}

          {current === 2 && (
            <Card type="inner" title={t('buildMetric.confirm', '确认信息')}>
              <p><strong>{t('buildMetric.code', '指标编码')}:</strong> {form.getFieldValue('code')}</p>
              <p><strong>{t('buildMetric.nameZh', '中文名')}:</strong> {form.getFieldValue(['name', 'zh_CN'])}</p>
              <p><strong>{t('buildMetric.metricType', '指标类型')}:</strong> {form.getFieldValue('metricType')}</p>
              <p><strong>{t('buildMetric.aggMethod', '聚合方式')}:</strong> {form.getFieldValue('aggMethod')}</p>
              <p><strong>{t('buildMetric.expression', '表达式')}:</strong> {form.getFieldValue('expression')}</p>
            </Card>
          )}

          {current === 3 && (
            <Result
              status="success"
              title={t('buildMetric.success', '指标创建成功')}
              subTitle={`Task ID: ${taskResult?.id}`}
              extra={
                <Button type="primary" onClick={() => { setCurrent(0); form.resetFields(); }}>
                  {t('buildMetric.createAnother', '继续创建')}
                </Button>
              }
            />
          )}
        </Form>

        {current < 3 && (
          <div style={{ marginTop: 24, textAlign: 'right' }}>
            <Space>
              {current > 0 && (
                <Button onClick={() => setCurrent(current - 1)}>
                  {t('common.prev', '上一步')}
                </Button>
              )}
              {current < 2 && (
                <Button type="primary" onClick={handleNext}>
                  {t('common.next', '下一步')}
                </Button>
              )}
              {current === 2 && (
                <Button type="primary" loading={submitting} onClick={handleSubmit}>
                  {t('common.submit', '提交')}
                </Button>
              )}
            </Space>
          </div>
        )}
      </Card>
    </div>
  );
}
