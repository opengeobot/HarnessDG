/**
 * 功能：数据接入向导页 - 5步表单引导用户完成数据接入配置
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useState } from 'react';
import { Card, Steps, Form, Input, Select, Button, Space, message, Table, Tag } from 'antd';
import { ArrowLeftOutlined, ArrowRightOutlined, CheckOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { ingestionApi } from '@/services/ingestionApi';
import { DictSelect } from '@/components/dict';

export default function IngestionWizard() {
  const { t } = useTranslation(['ingestion', 'common']);
  const [current, setCurrent] = useState(0);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [preview, setPreview] = useState<any>(null);

  const steps = [
    { title: t('ingestion.wizard.step1_title', '选择数据源') },
    { title: t('ingestion.wizard.step2_title', '选择目标实体') },
    { title: t('ingestion.wizard.step3_title', '字段映射') },
    { title: t('ingestion.wizard.step4_title', '同步配置') },
    { title: t('ingestion.wizard.step5_title', '预览提交') },
  ];

  const handleNext = async () => {
    try {
      await form.validateFields();
    } catch {
      return;
    }
    if (current === 4) {
      handleSubmit();
    } else {
      setCurrent(c => c + 1);
    }
  };

  const handleSubmit = async () => {
    const v = form.getFieldsValue();
    setSubmitting(true);
    try {
      await ingestionApi.createTask(v);
      message.success(t('ingestion.wizard.submit_success', '任务创建成功'));
      form.resetFields();
      setCurrent(0);
      setPreview(null);
    } catch {
      message.error(t('ingestion.wizard.submit_fail', '任务创建失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const handlePrev = () => setCurrent(c => Math.max(0, c - 1));

  const buildPreview = () => {
    const v = form.getFieldsValue();
    setPreview(v);
  };

  const renderStepContent = () => {
    switch (current) {
      case 0:
        return (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Form.Item name="sourceId" label={t('ingestion.select_source')} rules={[{ required: true }]}>
              <Select placeholder={t('ingestion.select_source_placeholder')} />
            </Form.Item>
          </Space>
        );
      case 1:
        return (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Form.Item name="targetEntity" label={t('ingestion.target_entity')} rules={[{ required: true }]}>
              <Input placeholder={t('ingestion.target_entity_placeholder')} />
            </Form.Item>
          </Space>
        );
      case 2:
        return (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <p style={{ color: '#888' }}>{t('ingestion.field_mapping_hint', '配置源表字段与目标实体字段的映射关系')}</p>
            <Form.List name="fieldMappings">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...restField }) => (
                    <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                      <Form.Item {...restField} name={[name, 'sourceField']}
                        rules={[{ required: true, message: t('common:validation.required', '必填') }]}>
                        <Input placeholder={t('ingestion.source_field', '源字段')} />
                      </Form.Item>
                      <Form.Item {...restField} name={[name, 'targetField']}
                        rules={[{ required: true, message: t('common:validation.required', '必填') }]}>
                        <Input placeholder={t('ingestion.target_field', '目标字段')} />
                      </Form.Item>
                      <Button onClick={() => remove(name)}>{t('common:action.delete', '删除')}</Button>
                    </Space>
                  ))}
                  <Button type="dashed" onClick={() => add()} block>
                    {t('ingestion.add_mapping', '添加映射')}
                  </Button>
                </>
              )}
            </Form.List>
          </Space>
        );
      case 3:
        return (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Form.Item name="mode" label={t('ingestion.sync_mode')} rules={[{ required: true }]}>
              <DictSelect groupCode="sync_mode" />
            </Form.Item>
            <Form.Item name="schedule" label={t('ingestion.schedule')}>
              <Input placeholder="0 0 * * * (cron expression)" />
            </Form.Item>
            <Form.Item name="incrementalField" label={t('ingestion.incremental_field')}>
              <Input placeholder={t('ingestion.incremental_field_placeholder', '增量字段（如 update_time）')} />
            </Form.Item>
          </Space>
        );
      case 4:
        return (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Button onClick={buildPreview}>{t('ingestion.build_preview', '生成预览')}</Button>
            {preview && (
              <Table
                size="small"
                pagination={false}
                dataSource={[
                  { key: 'sourceId', label: t('ingestion.source'), value: preview.sourceId },
                  { key: 'targetEntity', label: t('ingestion.target_entity'), value: preview.targetEntity },
                  { key: 'mode', label: t('ingestion.sync_mode'), value: preview.mode },
                  { key: 'schedule', label: t('ingestion.schedule'), value: preview.schedule || 'manual' },
                  { key: 'fieldMappings', label: t('ingestion.field_mappings'), value: JSON.stringify(preview.fieldMappings || []) },
                ]}
                columns={[
                  { title: t('ingestion.config_item', '配置项'), dataIndex: 'label' },
                  { title: t('ingestion.config_value', '配置值'), dataIndex: 'value',
                    render: (v: any) => typeof v === 'object' ? JSON.stringify(v) : String(v) },
                ]}
              />
            )}
          </Space>
        );
      default:
        return null;
    }
  };

  return (
    <Card title={t('ingestion.wizard.title', '数据接入向导')}>
      <Steps current={current} items={steps.map(s => ({ title: s.title }))} style={{ marginBottom: 32 }} />
      <Form form={form} layout="vertical">
        {renderStepContent()}
      </Form>
      <Space style={{ marginTop: 24 }}>
        {current > 0 && (
          <Button icon={<ArrowLeftOutlined />} onClick={handlePrev}>
            {t('common:action.prev', '上一步')}
          </Button>
        )}
        <Button type="primary" icon={current === 4 ? <CheckOutlined /> : <ArrowRightOutlined />}
          onClick={handleNext} loading={submitting}>
          {current === 4 ? t('ingestion.wizard.submit', '提交') : t('common:action.next', '下一步')}
        </Button>
      </Space>
    </Card>
  );
}
