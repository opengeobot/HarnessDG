/**
 * 功能: 资产血缘页面——展示上下游关系列表。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Button, Card, Empty, Flex, Select, Skeleton, Space, Table, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { getAssetLineage } from './api';
import type { AssetLineageView, LineageDirection } from './types';

export function AssetLineagePage() {
  const { assetId } = useParams<{ assetId: string }>();
  const { t } = useTranslation();
  const [direction, setDirection] = useState<LineageDirection>('down');
  const [depth, setDepth] = useState(3);

  useDocumentTitle(t('assets.lineage.title'));

  const query = useQuery({
    queryKey: ['asset-lineage', assetId, direction, depth],
    queryFn: () => getAssetLineage(assetId!, { direction, depth }),
    enabled: !!assetId,
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
    </Flex>
  );
}
