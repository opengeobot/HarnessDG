/**
 * 功能：质量规则管理页 - 规则 CRUD、自动规则生成
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Input, Select, Modal, Form, message, Popconfirm, Drawer,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { qualityApi } from '@/services/qualityApi';
import { DictSelect, DictTag } from '@/components/dict';

interface QualityRule {
  id: number;
  name: string;
  entityId: number;
  entityName: string;
  ruleType: string;
  severity: string;
  expression: string;
  status: string;
  createdAt: string;
}

export default function QualityRulePage() {
  const { t } = useTranslation(['quality', 'common']);
  const [data, setData] = useState<QualityRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterEntity, setFilterEntity] = useState<string>();
  const [filterType, setFilterType] = useState<string>();
  const [filterStatus, setFilterStatus] = useState<string>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<QualityRule | null>(null);
  const [form] = Form.useForm();
  const [autoModal, setAutoModal] = useState(false);
  const [autoForm] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const params: any = {};
      if (filterEntity) params.entityId = filterEntity;
      if (filterType) params.ruleType = filterType;
      if (filterStatus) params.status = filterStatus;
      const res: any = await qualityApi.listRules(params);
      setData(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [filterEntity, filterType, filterStatus]);

  const handleSave = async () => {
    const v = await form.validateFields();
    try {
      if (editingRule) {
        await qualityApi.updateRule(editingRule.id, v);
      } else {
        await qualityApi.createRule(v);
      }
      message.success(t('common:action.save_success', '保存成功'));
      setModalOpen(false);
      loadData();
    } catch {
      message.error(t('common:action.save_fail', '保存失败'));
    }
  };

  const openCreate = () => {
    setEditingRule(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (r: QualityRule) => {
    setEditingRule(r);
    form.setFieldsValue(r);
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await qualityApi.deleteRule(id);
      message.success(t('common:action.delete_success', '删除成功'));
      loadData();
    } catch {
      message.error(t('common:action.delete_fail', '删除失败'));
    }
  };

  const handleAutoGenerate = async () => {
    const v = await autoForm.validateFields();
    try {
      await qualityApi.autoGenerateRules(v);
      message.success(t('quality.auto_generate_success', '自动规则生成成功'));
      setAutoModal(false);
      loadData();
    } catch {
      message.error(t('quality.auto_generate_fail', '自动规则生成失败'));
    }
  };

  const columns: ColumnsType<QualityRule> = [
    { title: t('quality.rule_name'), dataIndex: 'name', ellipsis: true },
    { title: t('quality.entity'), dataIndex: 'entityName', width: 140 },
    {
      title: t('quality.rule_type'),
      dataIndex: 'ruleType',
      width: 120,
      render: (v: string) => <DictTag groupCode="quality_rule_type" code={v} />,
    },
    {
      title: t('quality.severity'),
      dataIndex: 'severity',
      width: 100,
      render: (v: string) => <DictTag groupCode="quality_severity" code={v} />,
    },
    {
      title: t('quality.status'),
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <DictTag groupCode="quality_status" code={v} />,
    },
    { title: t('quality.expression'), dataIndex: 'expression', ellipsis: true, width: 200 },
    { title: t('quality.created_at'), dataIndex: 'createdAt', width: 180 },
    {
      title: t('common:action.title', '操作'),
      fixed: 'right',
      width: 160,
      render: (_, r) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            {t('common:action.edit', '编辑')}
          </Button>
          <Popconfirm title={t('common:confirm.delete_desc')} onConfirm={() => handleDelete(r.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>{t('common:action.delete', '删除')}</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('quality.title', '质量规则')}
      extra={
        <Space>
          <Input placeholder={t('quality.entity_id_placeholder', '实体ID')} value={filterEntity}
            onChange={e => setFilterEntity(e.target.value)} allowClear style={{ width: 120 }} />
          <Select allowClear placeholder={t('quality.rule_type')} value={filterType}
            onChange={setFilterType} style={{ width: 140 }}>
            <Select.Option value="completeness">completeness</Select.Option>
            <Select.Option value="accuracy">accuracy</Select.Option>
            <Select.Option value="uniqueness">uniqueness</Select.Option>
            <Select.Option value="timeliness">timeliness</Select.Option>
            <Select.Option value="validity">validity</Select.Option>
            <Select.Option value="consistency">consistency</Select.Option>
          </Select>
          <Select allowClear placeholder={t('quality.status')} value={filterStatus}
            onChange={setFilterStatus} style={{ width: 120 }}
            options={[
              { value: 'active', label: t('quality.status_active', '启用') },
              { value: 'inactive', label: t('quality.status_inactive', '停用') },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={loadData} />
          <Button icon={<ThunderboltOutlined />} onClick={() => setAutoModal(true)}>
            {t('quality.auto_generate', '自动规则生成')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('quality.create', '新建规则')}
          </Button>
        </Space>
      }
    >
      <Table<QualityRule>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1200 }}
      />

      <Drawer
        open={modalOpen}
        title={editingRule ? t('quality.edit', '编辑规则') : t('quality.create', '新建规则')}
        onClose={() => setModalOpen(false)}
        width={560}
        extra={<Button type="primary" onClick={handleSave}>{t('common:action.save', '保存')}</Button>}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('quality.rule_name')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="entityId" label={t('quality.entity_id')} rules={[{ required: true }]}>
            <Input type="number" />
          </Form.Item>
          <Form.Item name="ruleType" label={t('quality.rule_type')} rules={[{ required: true }]}>
            <DictSelect groupCode="quality_rule_type" />
          </Form.Item>
          <Form.Item name="severity" label={t('quality.severity')} rules={[{ required: true }]}>
            <DictSelect groupCode="quality_severity" />
          </Form.Item>
          <Form.Item name="expression" label={t('quality.expression')} rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="SQL expression, e.g. count(*) > 0" />
          </Form.Item>
          <Form.Item name="status" label={t('quality.status')}>
            <DictSelect groupCode="quality_status" />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        open={autoModal}
        title={t('quality.auto_generate', '自动规则生成')}
        onCancel={() => setAutoModal(false)}
        onOk={handleAutoGenerate}
      >
        <Form form={autoForm} layout="vertical">
          <Form.Item name="entityId" label={t('quality.entity_id')} rules={[{ required: true }]}>
            <Input type="number" />
          </Form.Item>
          <Form.Item name="ruleTypes" label={t('quality.rule_types_to_generate')}>
            <Select mode="multiple" placeholder={t('quality.select_rule_types')}>
              <Select.Option value="completeness">completeness</Select.Option>
              <Select.Option value="accuracy">accuracy</Select.Option>
              <Select.Option value="uniqueness">uniqueness</Select.Option>
              <Select.Option value="validity">validity</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
