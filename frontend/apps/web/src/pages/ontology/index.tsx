/**
 * 功能：本体建模 CRUD 页面
 * 时间：2026-05-12
 * 作者：AxeXie
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Card, Table, Button, Space, Tag, Modal, Form, Input, Select,
  message, Popconfirm, Tabs, Spin, Empty,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { TabsProps } from 'antd';
import {
  PlusOutlined, ReloadOutlined, DeleteOutlined, EditOutlined,
  DeploymentUnitOutlined, UnorderedListOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { ontologyApi } from '@/services';

/* ==================== Types ==================== */

interface OntologyEntity {
  id: number;
  code: string;
  name: Record<string, string>;
  description: Record<string, string>;
  domain?: string;
  status: string;
}

interface OntologyMetric {
  id: number;
  entityId: number;
  code: string;
  name: Record<string, string>;
  description: Record<string, string>;
  metricType: string;
  aggMethod: string;
  expression?: string;
  unit?: string;
  status: string;
}

interface OntologyDimension {
  id: number;
  entityId: number;
  code: string;
  name: Record<string, string>;
  description: Record<string, string>;
  dataType: string;
  status: string;
}

/* ==================== Component ==================== */

export default function OntologyPage() {
  const { t, i18n } = useTranslation('ontology');
  const locale = i18n.language || 'zh_CN';

  // --- Entity List State ---
  const [entities, setEntities] = useState<OntologyEntity[]>([]);
  const [totalEntities, setTotalEntities] = useState(0);
  const [entityPage, setEntityPage] = useState(1);
  const [entitySize] = useState(10);
  const [keyword, setKeyword] = useState<string>();
  const [domainFilter, setDomainFilter] = useState<string>();
  const [statusFilter, setStatusFilter] = useState<string>();
  const [loadingEntities, setLoadingEntities] = useState(false);

  // --- Selected Entity ---
  const [selectedEntity, setSelectedEntity] = useState<OntologyEntity | null>(null);

  // --- Create Entity Modal ---
  const [entityModalOpen, setEntityModalOpen] = useState(false);
  const [entitySubmitting, setEntitySubmitting] = useState(false);
  const [entityForm] = Form.useForm();

  // --- Edit Entity Form ---
  const [editForm] = Form.useForm();
  const [editSaving, setEditSaving] = useState(false);

  // --- Metrics ---
  const [metrics, setMetrics] = useState<OntologyMetric[]>([]);
  const [loadingMetrics, setLoadingMetrics] = useState(false);
  const [metricFormOpen, setMetricFormOpen] = useState(false);
  const [metricSubmitting, setMetricSubmitting] = useState(false);
  const [metricForm] = Form.useForm();

  // --- Dimensions ---
  const [dimensions, setDimensions] = useState<OntologyDimension[]>([]);
  const [loadingDimensions, setLoadingDimensions] = useState(false);
  const [dimensionFormOpen, setDimensionFormOpen] = useState(false);
  const [dimensionSubmitting, setDimensionSubmitting] = useState(false);
  const [dimensionForm] = Form.useForm();

  /* ==================== Helpers ==================== */

  const localeLabel = (m?: Record<string, string>) =>
    m?.[locale] || m?.['zh_CN'] || m?.['en_US'] || '-';

  const statusColorMap: Record<string, string> = {
    active: 'green',
    inactive: 'default',
    draft: 'orange',
  };

  /* ==================== Entity Operations ==================== */

  const loadEntities = async () => {
    setLoadingEntities(true);
    try {
      const res: any = await ontologyApi.listEntities({
        keyword,
        domain: domainFilter,
        status: statusFilter,
        page: entityPage,
        size: entitySize,
      });
      setEntities(res.data?.items || res.data || []);
      setTotalEntities(res.data?.total || 0);
    } catch (err: any) {
      message.error(err?.message || t('entity.loadError', 'Failed to load entities'));
    } finally {
      setLoadingEntities(false);
    }
  };

  useEffect(() => {
    loadEntities();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityPage, keyword, domainFilter, statusFilter]);

  const handleSelectEntity = async (entity: OntologyEntity) => {
    setSelectedEntity(entity);
    editForm.setFieldsValue({
      code: entity.code,
      nameZh: entity.name?.zh_CN,
      nameEn: entity.name?.en_US,
      descZh: entity.description?.zh_CN,
      descEn: entity.description?.en_US,
      domain: entity.domain,
      status: entity.status,
    });
    loadMetrics(entity.id);
    loadDimensions(entity.id);
  };

  const openCreateEntity = () => {
    entityForm.resetFields();
    entityForm.setFieldsValue({ status: 'active' });
    setEntityModalOpen(true);
  };

  const handleCreateEntity = async () => {
    const v = await entityForm.validateFields();
    setEntitySubmitting(true);
    try {
      await ontologyApi.createEntity({
        code: v.code,
        name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
        description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
        domain: v.domain,
        status: v.status || 'active',
      });
      message.success(t('entity.createSuccess', 'Entity created successfully'));
      setEntityModalOpen(false);
      loadEntities();
    } catch (err: any) {
      message.error(err?.message || t('entity.createError', 'Failed to create entity'));
    } finally {
      setEntitySubmitting(false);
    }
  };

  const handleDeleteEntity = async (entity: OntologyEntity) => {
    try {
      await ontologyApi.deleteEntity(entity.id);
      message.success(t('entity.deleteSuccess', 'Entity deleted successfully'));
      if (selectedEntity?.id === entity.id) {
        setSelectedEntity(null);
      }
      loadEntities();
    } catch (err: any) {
      message.error(err?.message || t('entity.deleteError', 'Failed to delete entity'));
    }
  };

  const handleSaveEntity = async () => {
    if (!selectedEntity) return;
    const v = await editForm.validateFields();
    setEditSaving(true);
    try {
      await ontologyApi.updateEntity(selectedEntity.id, {
        name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
        description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
        domain: v.domain,
        status: v.status,
      });
      message.success(t('entity.updateSuccess', 'Entity updated successfully'));
      loadEntities();
      // refresh selected entity display
      setSelectedEntity((prev) =>
        prev
          ? {
              ...prev,
              name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
              description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
              domain: v.domain,
              status: v.status,
            }
          : null,
      );
    } catch (err: any) {
      message.error(err?.message || t('entity.updateError', 'Failed to update entity'));
    } finally {
      setEditSaving(false);
    }
  };

  /* ==================== Metric Operations ==================== */

  const loadMetrics = async (entityId: number) => {
    setLoadingMetrics(true);
    try {
      const res: any = await ontologyApi.listMetrics(entityId);
      setMetrics(res.data || []);
    } catch (err: any) {
      message.error(err?.message || t('metric.loadError', 'Failed to load metrics'));
    } finally {
      setLoadingMetrics(false);
    }
  };

  const handleCreateMetric = async () => {
    if (!selectedEntity) return;
    const v = await metricForm.validateFields();
    setMetricSubmitting(true);
    try {
      await ontologyApi.createMetric({
        entityId: selectedEntity.id,
        code: v.code,
        name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
        description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
        metricType: v.metricType,
        aggMethod: v.aggMethod,
        expression: v.expression,
        unit: v.unit,
      });
      message.success(t('metric.createSuccess', 'Metric created successfully'));
      setMetricFormOpen(false);
      metricForm.resetFields();
      loadMetrics(selectedEntity.id);
    } catch (err: any) {
      message.error(err?.message || t('metric.createError', 'Failed to create metric'));
    } finally {
      setMetricSubmitting(false);
    }
  };

  /* ==================== Dimension Operations ==================== */

  const loadDimensions = async (entityId: number) => {
    setLoadingDimensions(true);
    try {
      const res: any = await ontologyApi.listDimensions(entityId);
      setDimensions(res.data || []);
    } catch (err: any) {
      message.error(err?.message || t('dimension.loadError', 'Failed to load dimensions'));
    } finally {
      setLoadingDimensions(false);
    }
  };

  const handleCreateDimension = async () => {
    if (!selectedEntity) return;
    const v = await dimensionForm.validateFields();
    setDimensionSubmitting(true);
    try {
      await ontologyApi.createDimension({
        entityId: selectedEntity.id,
        code: v.code,
        name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
        description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
        dataType: v.dataType,
      });
      message.success(t('dimension.createSuccess', 'Dimension created successfully'));
      setDimensionFormOpen(false);
      dimensionForm.resetFields();
      loadDimensions(selectedEntity.id);
    } catch (err: any) {
      message.error(err?.message || t('dimension.createError', 'Failed to create dimension'));
    } finally {
      setDimensionSubmitting(false);
    }
  };

  /* ==================== Entity Table Columns ==================== */

  const entityCols: ColumnsType<OntologyEntity> = [
    {
      title: t('entity.code', 'Code'),
      dataIndex: 'code',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('entity.name', 'Name'),
      render: (_, r) => localeLabel(r.name),
    },
    {
      title: t('entity.domain', 'Domain'),
      dataIndex: 'domain',
      width: 120,
      render: (d: string) => d || '-',
    },
    {
      title: t('entity.status', 'Status'),
      dataIndex: 'status',
      width: 90,
      render: (s: string) => (
        <Tag color={statusColorMap[s] || 'default'}>{s}</Tag>
      ),
    },
    {
      title: t('entity.actions', 'Actions'),
      fixed: 'right',
      width: 100,
      render: (_, r) => (
        <Popconfirm
          title={t('entity.deleteConfirm', 'Are you sure to delete this entity?')}
          onConfirm={() => handleDeleteEntity(r)}
        >
          <Button size="small" danger icon={<DeleteOutlined />}>
            {t('common:action.delete', 'Delete')}
          </Button>
        </Popconfirm>
      ),
    },
  ];

  /* ==================== Metric Table Columns ==================== */

  const metricCols: ColumnsType<OntologyMetric> = [
    {
      title: t('metric.code', 'Code'),
      dataIndex: 'code',
      width: 160,
    },
    {
      title: t('metric.name', 'Name'),
      render: (_, r) => localeLabel(r.name),
    },
    {
      title: t('metric.metricType', 'Type'),
      dataIndex: 'metricType',
      width: 100,
      render: (type: string) => {
        const typeMap: Record<string, string> = {
          atomic: 'blue',
          derived: 'green',
          composite: 'purple',
        };
        return <Tag color={typeMap[type] || 'default'}>{type}</Tag>;
      },
    },
    {
      title: t('metric.aggMethod', 'Aggregation'),
      dataIndex: 'aggMethod',
      width: 100,
    },
    {
      title: t('metric.unit', 'Unit'),
      dataIndex: 'unit',
      width: 80,
      render: (u: string) => u || '-',
    },
    {
      title: t('metric.status', 'Status'),
      dataIndex: 'status',
      width: 80,
      render: (s: string) => <Tag color={statusColorMap[s] || 'default'}>{s}</Tag>,
    },
  ];

  /* ==================== Dimension Table Columns ==================== */

  const dimensionCols: ColumnsType<OntologyDimension> = [
    {
      title: t('dimension.code', 'Code'),
      dataIndex: 'code',
      width: 160,
    },
    {
      title: t('dimension.name', 'Name'),
      render: (_, r) => localeLabel(r.name),
    },
    {
      title: t('dimension.dataType', 'Data Type'),
      dataIndex: 'dataType',
      width: 120,
      render: (dt: string) => <Tag>{dt}</Tag>,
    },
    {
      title: t('dimension.status', 'Status'),
      dataIndex: 'status',
      width: 80,
      render: (s: string) => <Tag color={statusColorMap[s] || 'default'}>{s}</Tag>,
    },
  ];

  /* ==================== Tabs for Detail Panel ==================== */

  const tabItems: TabsProps['items'] = useMemo(() => {
    if (!selectedEntity) return [];
    return [
      {
        key: 'basic',
        label: (
          <span>
            <EditOutlined /> {t('tabs.basic', 'Basic Info')}
          </span>
        ),
        children: (
          <Form form={editForm} layout="vertical" style={{ maxWidth: 600 }}>
            <Form.Item name="code" label={t('entity.code', 'Code')}>
              <Input disabled />
            </Form.Item>
            <Form.Item name="nameZh" label={t('entity.name', 'Name') + ' (中文)'} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="nameEn" label={t('entity.name', 'Name') + ' (English)'}>
              <Input />
            </Form.Item>
            <Form.Item name="descZh" label={t('entity.description', 'Description') + ' (中文)'}>
              <Input.TextArea rows={2} />
            </Form.Item>
            <Form.Item name="descEn" label={t('entity.description', 'Description') + ' (English)'}>
              <Input.TextArea rows={2} />
            </Form.Item>
            <Form.Item name="domain" label={t('entity.domain', 'Domain')}>
              <Input />
            </Form.Item>
            <Form.Item name="status" label={t('entity.status', 'Status')}>
              <Select
                options={[
                  { label: 'active', value: 'active' },
                  { label: 'inactive', value: 'inactive' },
                  { label: 'draft', value: 'draft' },
                ]}
              />
            </Form.Item>
            <Form.Item>
              <Button
                type="primary"
                onClick={handleSaveEntity}
                loading={editSaving}
              >
                {t('common:action.save', 'Save')}
              </Button>
            </Form.Item>
          </Form>
        ),
      },
      {
        key: 'metrics',
        label: (
          <span>
            <UnorderedListOutlined /> {t('tabs.metrics', 'Metrics')}
          </span>
        ),
        children: (
          <div>
            <div style={{ marginBottom: 16 }}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  metricForm.resetFields();
                  setMetricFormOpen(true);
                }}
              >
                {t('metric.create', 'Create Metric')}
              </Button>
            </div>
            <Spin spinning={loadingMetrics}>
              <Table<OntologyMetric>
                rowKey="id"
                size="small"
                columns={metricCols}
                dataSource={metrics}
                pagination={{ pageSize: 10 }}
                locale={{ emptyText: t('metric.empty', 'No metrics yet') }}
              />
            </Spin>
          </div>
        ),
      },
      {
        key: 'dimensions',
        label: (
          <span>
            <UnorderedListOutlined /> {t('tabs.dimensions', 'Dimensions')}
          </span>
        ),
        children: (
          <div>
            <div style={{ marginBottom: 16 }}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  dimensionForm.resetFields();
                  setDimensionFormOpen(true);
                }}
              >
                {t('dimension.create', 'Create Dimension')}
              </Button>
            </div>
            <Spin spinning={loadingDimensions}>
              <Table<OntologyDimension>
                rowKey="id"
                size="small"
                columns={dimensionCols}
                dataSource={dimensions}
                pagination={{ pageSize: 10 }}
                locale={{ emptyText: t('dimension.empty', 'No dimensions yet') }}
              />
            </Spin>
          </div>
        ),
      },
    ];
  }, [selectedEntity, metrics, dimensions, loadingMetrics, loadingDimensions, editSaving, metricCols, dimensionCols]);

  /* ==================== Render ==================== */

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(320px, 420px) 1fr',
        gap: 16,
        minHeight: '100%',
      }}
      className="ontology-page"
    >
      {/* ---- Left: Entity List ---- */}
      <Card
        title={
          <span>
            <DeploymentUnitOutlined style={{ marginRight: 8, color: 'var(--color-brand-primary)' }} />
            {t('entity.title', 'Entities')}
          </span>
        }
        size="small"
        extra={
          <Space size={4}>
            <Select
              allowClear
              size="small"
              placeholder={t('entity.domain', 'Domain')}
              value={domainFilter}
              onChange={setDomainFilter}
              style={{ width: 110 }}
              options={[
                { value: 'business', label: 'Business' },
                { value: 'technical', label: 'Technical' },
                { value: 'analytics', label: 'Analytics' },
              ]}
            />
            <Select
              allowClear
              size="small"
              placeholder={t('entity.status', 'Status')}
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 100 }}
              options={[
                { value: 'active', label: 'Active' },
                { value: 'inactive', label: 'Inactive' },
                { value: 'draft', label: 'Draft' },
              ]}
            />
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => {
                setEntityPage(1);
                loadEntities();
              }}
            />
            <Button
              size="small"
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreateEntity}
            />
          </Space>
        }
        styles={{ body: { padding: 0 } }}
      >
        <div style={{ padding: '12px 12px 0' }}>
          <Input.Search
            placeholder={t('entity.searchPlaceholder', 'Search by code or name')}
            allowClear
            size="small"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setEntityPage(1);
            }}
            onSearch={setKeyword}
            style={{ marginBottom: 8 }}
          />
        </div>
        <Table<OntologyEntity>
          rowKey="id"
          size="small"
          loading={loadingEntities}
          columns={entityCols}
          dataSource={entities}
          pagination={{
            current: entityPage,
            pageSize: entitySize,
            total: totalEntities,
            showSizeChanger: false,
            size: 'small',
            onChange: (p) => setEntityPage(p),
          }}
          onRow={(r) => ({
            onClick: () => handleSelectEntity(r),
            style: {
              cursor: 'pointer',
              backgroundColor: selectedEntity?.id === r.id
                ? 'var(--color-brand-primary-light, #e6f4ff)'
                : undefined,
            },
          })}
          scroll={{ y: 'calc(100vh - 340px)' }}
        />
      </Card>

      {/* ---- Right: Entity Detail ---- */}
      <Card
        size="small"
        title={
          selectedEntity
            ? `${localeLabel(selectedEntity.name)} (${selectedEntity.code})`
            : t('entity.selectPrompt', 'Select an entity to view details')
        }
      >
        {selectedEntity ? (
          <Tabs items={tabItems} />
        ) : (
          <Empty
            image={<DeploymentUnitOutlined style={{ fontSize: 64, color: '#bfbfbf' }} />}
            description={t('entity.selectPrompt', 'Select an entity to view details')}
          />
        )}
      </Card>

      {/* ---- Create Entity Modal ---- */}
      <Modal
        open={entityModalOpen}
        title={t('entity.createTitle', 'Create Entity')}
        onCancel={() => setEntityModalOpen(false)}
        onOk={handleCreateEntity}
        confirmLoading={entitySubmitting}
        width={520}
      >
        <Form form={entityForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="code" label={t('entity.code', 'Code')} rules={[{ required: true }]}>
            <Input placeholder="e.g. order, product" />
          </Form.Item>
          <Form.Item name="nameZh" label={t('entity.name', 'Name') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameEn" label={t('entity.name', 'Name') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="descZh" label={t('entity.description', 'Description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('entity.description', 'Description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="domain" label={t('entity.domain', 'Domain')}>
            <Select
              allowClear
              options={[
                { value: 'business', label: 'Business' },
                { value: 'technical', label: 'Technical' },
                { value: 'analytics', label: 'Analytics' },
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label={t('entity.status', 'Status')}>
            <Select
              options={[
                { label: 'active', value: 'active' },
                { label: 'inactive', value: 'inactive' },
                { label: 'draft', value: 'draft' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* ---- Create Metric Modal ---- */}
      <Modal
        open={metricFormOpen}
        title={t('metric.createTitle', 'Create Metric')}
        onCancel={() => setMetricFormOpen(false)}
        onOk={handleCreateMetric}
        confirmLoading={metricSubmitting}
        width={560}
      >
        <Form form={metricForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="code" label={t('metric.code', 'Code')} rules={[{ required: true }]}>
            <Input placeholder="e.g. revenue_total" />
          </Form.Item>
          <Form.Item name="nameZh" label={t('metric.name', 'Name') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameEn" label={t('metric.name', 'Name') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="descZh" label={t('metric.description', 'Description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('metric.description', 'Description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="metricType" label={t('metric.metricType', 'Metric Type')} rules={[{ required: true }]}>
            <Select
              options={[
                { label: t('metric.type.atomic', 'Atomic'), value: 'atomic' },
                { label: t('metric.type.derived', 'Derived'), value: 'derived' },
                { label: t('metric.type.composite', 'Composite'), value: 'composite' },
              ]}
            />
          </Form.Item>
          <Form.Item name="aggMethod" label={t('metric.aggMethod', 'Aggregation Method')} rules={[{ required: true }]}>
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
          <Form.Item name="expression" label={t('metric.expression', 'Expression')}>
            <Input.TextArea rows={2} placeholder="e.g. SUM(order_amount)" />
          </Form.Item>
          <Form.Item name="unit" label={t('metric.unit', 'Unit')}>
            <Input placeholder="e.g. 元, 个, %" />
          </Form.Item>
        </Form>
      </Modal>

      {/* ---- Create Dimension Modal ---- */}
      <Modal
        open={dimensionFormOpen}
        title={t('dimension.createTitle', 'Create Dimension')}
        onCancel={() => setDimensionFormOpen(false)}
        onOk={handleCreateDimension}
        confirmLoading={dimensionSubmitting}
        width={520}
      >
        <Form form={dimensionForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="code" label={t('dimension.code', 'Code')} rules={[{ required: true }]}>
            <Input placeholder="e.g. date, region" />
          </Form.Item>
          <Form.Item name="nameZh" label={t('dimension.name', 'Name') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameEn" label={t('dimension.name', 'Name') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="descZh" label={t('dimension.description', 'Description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('dimension.description', 'Description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="dataType" label={t('dimension.dataType', 'Data Type')} rules={[{ required: true }]}>
            <Select
              options={[
                { label: 'STRING', value: 'string' },
                { label: 'INTEGER', value: 'integer' },
                { label: 'BIGINT', value: 'bigint' },
                { label: 'DOUBLE', value: 'double' },
                { label: 'DECIMAL', value: 'decimal' },
                { label: 'DATE', value: 'date' },
                { label: 'DATETIME', value: 'datetime' },
                { label: 'BOOLEAN', value: 'boolean' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
