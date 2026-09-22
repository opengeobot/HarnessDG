/**
 * 功能: 资产 ACL 管理页面——列出/创建/删除资产级显式授权。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import {
  App,
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { isApiError } from '@/shared/api';
import { createAssetAccess, deleteAssetAccess, getAsset, listAssetAccess } from './api';
import type { ResourceAclView } from './types';

const PERMISSION_OPTIONS = [
  { value: 'asset:read', label: 'asset:read' },
  { value: 'asset:update', label: 'asset:update' },
  { value: 'asset:manage', label: 'asset:manage' },
];

export function AssetAccessPage() {
  const { assetId } = useParams<{ assetId: string }>();
  const { t } = useTranslation();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();

  useDocumentTitle(t('assets.access.title'));

  const assetQuery = useQuery({
    queryKey: ['asset', assetId],
    queryFn: () => getAsset(assetId!),
    enabled: !!assetId,
  });

  const aclQuery = useQuery({
    queryKey: ['asset-access', assetId],
    queryFn: () => listAssetAccess(assetId!),
    enabled: !!assetId,
  });

  const createMutation = useMutation({
    mutationFn: (values: { principalId: string; permissionCodes: string[] }) =>
      createAssetAccess(assetId!, values),
    onSuccess: () => {
      message.success(t('assets.access.created'));
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['asset-access', assetId] });
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : t('common.operationFailed'));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (aclId: string) => deleteAssetAccess(assetId!, aclId),
    onSuccess: () => {
      message.success(t('assets.access.deleted'));
      void queryClient.invalidateQueries({ queryKey: ['asset-access', assetId] });
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : t('common.operationFailed'));
    },
  });

  const columns: ColumnsType<ResourceAclView> = [
    {
      title: t('assets.access.principal'),
      dataIndex: 'principalId',
      key: 'principalId',
      render: (id: string) => <Typography.Text code>{id}</Typography.Text>,
    },
    {
      title: t('assets.access.permissions'),
      dataIndex: 'permissionCodes',
      key: 'permissionCodes',
      render: (codes: string[]) => (
        <Space wrap>
          {codes.map((code) => (
            <Tag key={code}>{code}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('assets.columns.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title={t('assets.access.confirmDelete')}
          onConfirm={() => deleteMutation.mutate(record.aclId)}
        >
          <Button danger type="link" size="small">
            {t('common.delete')}
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const asset = assetQuery.data;

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Space direction="vertical" size={0}>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('assets.access.title')}
          </Typography.Title>
          {asset ? (
            <Typography.Text type="secondary">
              <Link to={`/assets/${assetId}`}>{asset.displayName || asset.name}</Link>
              {' · '}
              <Typography.Text code>{asset.namespace}/{asset.name}</Typography.Text>
            </Typography.Text>
          ) : null}
        </Space>
        <Link to={`/assets/${assetId}/settings`}>
          <Button>{t('assets.detail.settingsLink')}</Button>
        </Link>
      </Flex>

      <Card title={t('assets.access.grant')} size="small">
        <Form
          form={form}
          layout="inline"
          onFinish={(values: { principalId: string; permissionCodes: string[] }) =>
            createMutation.mutate(values)
          }
        >
          <Form.Item
            name="principalId"
            rules={[{ required: true, message: t('assets.access.principalRequired') }]}
          >
            <Input placeholder={t('assets.access.principalPlaceholder')} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item
            name="permissionCodes"
            rules={[{ required: true, message: t('assets.access.permissionsRequired') }]}
          >
            <Select
              mode="multiple"
              options={PERMISSION_OPTIONS}
              placeholder={t('assets.access.permissionsPlaceholder')}
              style={{ minWidth: 260 }}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={createMutation.isPending}>
              {t('assets.access.grantButton')}
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Card title={t('assets.access.list')} size="small">
        <Table<ResourceAclView>
          rowKey="aclId"
          columns={columns}
          dataSource={aclQuery.data ?? []}
          loading={aclQuery.isLoading}
          pagination={false}
          locale={{ emptyText: <Empty description={t('assets.access.empty')} /> }}
        />
      </Card>
    </Flex>
  );
}
