/**
 * 功能: 资产血缘页面——展示上下游关系列表，支持添加关系。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import {
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Modal,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { createAssetRelation, getAssetLineage, searchAssets } from './api';
import type { AssetLineageView, LineageDirection } from './types';

const RELATION_TYPES = [
  'DERIVED_FROM',
  'TRAINED_ON',
  'BASED_ON',
  'FINE_TUNED_FROM',
] as const;

export function AssetLineagePage() {
  const { assetId } = useParams<{ assetId: string }>();
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [direction, setDirection] = useState<LineageDirection>('down');
  const [depth, setDepth] = useState(3);
  const [modalOpen, setModalOpen] = useState(false);
  const [childSearch, setChildSearch] = useState('');
  const [form] = Form.useForm<{ childAssetId: string; relationType: string }>();

  useDocumentTitle(t('assets.lineage.title'));

  const query = useQuery({
    queryKey: ['asset-lineage', assetId, direction, depth],
    queryFn: () => getAssetLineage(assetId!, { direction, depth }),
    enabled: !!assetId,
  });

  const childAssetQuery = useQuery({
    queryKey: ['asset-lineage-child-search', childSearch],
    queryFn: () => searchAssets({ keyword: childSearch, limit: 20 }),
    enabled: modalOpen && childSearch.length >= 1,
  });

  const childOptions = useMemo(
    () =>
      (childAssetQuery.data?.items ?? [])
        .filter((item) => item.assetId !== assetId)
        .map((item) => ({
          value: item.assetId,
          label: `${item.displayName ?? item.name} (${item.assetId})`,
        })),
    [childAssetQuery.data?.items, assetId],
  );

  const createMutation = useMutation({
    mutationFn: (values: { childAssetId: string; relationType: string }) =>
      createAssetRelation(assetId!, values),
    onSuccess: () => {
      message.success(t('assets.lineage.createSuccess'));
      setModalOpen(false);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['asset-lineage', assetId] });
    },
    onError: () => {
      message.error(t('assets.lineage.createFailed'));
    },
  });

  if (query.isLoading) {
    return (
      <Flex vertical gap={16}>
        <Skeleton.Input active size="large" style={{ width: 300 }} />
        <Skeleton active paragraph={{ rows: 6 }} />
      </Flex>
    );
  }

  const lineage: AssetLineageView | undefined = query.data;

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Space>
          <Link to={`/assets/${assetId}`}>
            <Button>{t('common.back')}</Button>
          </Link>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('assets.lineage.title')}
          </Typography.Title>
          {assetId && (
            <Typography.Text code>{assetId}</Typography.Text>
          )}
        </Space>
        <Space>
          <Button type="primary" onClick={() => setModalOpen(true)}>
            {t('assets.lineage.addRelation')}
          </Button>
          <Select
            value={direction}
            onChange={setDirection}
            style={{ width: 140 }}
            options={[
              { value: 'down', label: t('assets.lineage.directionDown') },
              { value: 'up', label: t('assets.lineage.directionUp') },
            ]}
          />
          <Select
            value={depth}
            onChange={setDepth}
            style={{ width: 100 }}
            options={[1, 2, 3, 5, 10].map((d) => ({ value: d, label: String(d) }))}
          />
        </Space>
      </Flex>

      <Card title={t('assets.lineage.relations')} size="small">
        <Table
          rowKey="relationId"
          size="small"
          loading={query.isFetching}
          dataSource={lineage?.relations ?? []}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          locale={{ emptyText: <Empty description={t('assets.lineage.empty')} /> }}
          columns={[
            {
              title: t('assets.lineage.relationType'),
              dataIndex: 'relationType',
              render: (type: string) => <Tag>{type}</Tag>,
            },
            {
              title: t('assets.lineage.parent'),
              dataIndex: 'parentAssetId',
              render: (id: string) => (
                <Link to={`/assets/${id}`}><Typography.Text code>{id}</Typography.Text></Link>
              ),
            },
            {
              title: t('assets.lineage.child'),
              dataIndex: 'childAssetId',
              render: (id: string) => (
                <Link to={`/assets/${id}`}><Typography.Text code>{id}</Typography.Text></Link>
              ),
            },
            {
              title: t('assets.lineage.hopDepth'),
              dataIndex: 'hopDepth',
              width: 80,
            },
            {
              title: t('assets.lineage.createdAt'),
              dataIndex: 'createdAt',
              render: (v: string | undefined) => v ? new Date(v).toLocaleString() : '-',
            },
          ]}
        />
      </Card>

      <Modal
        title={t('assets.lineage.addRelation')}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item
            name="childAssetId"
            label={t('assets.lineage.childAsset')}
            rules={[{ required: true, message: t('assets.lineage.childAssetRequired') }]}
          >
            <Select
              showSearch
              filterOption={false}
              onSearch={setChildSearch}
              options={childOptions}
              placeholder={t('assets.lineage.childAssetPlaceholder')}
              loading={childAssetQuery.isFetching}
            />
          </Form.Item>
          <Form.Item
            name="relationType"
            label={t('assets.lineage.relationType')}
            rules={[{ required: true, message: t('assets.lineage.relationTypeRequired') }]}
          >
            <Select
              options={RELATION_TYPES.map((type) => ({ value: type, label: type }))}
              placeholder={t('assets.lineage.relationTypePlaceholder')}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
