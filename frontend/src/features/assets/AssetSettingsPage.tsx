/**
 * 功能: 资产设置页面——编辑元数据、生命周期操作（弃用/归档/恢复/删除）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useCallback } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import {
  App,
  Badge,
  Button,
  Card,
  Descriptions,
  Flex,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Spin,
  Typography,
  Divider,
} from 'antd';
import {
  ExclamationCircleOutlined,
  DeleteOutlined,
  InboxOutlined,
  StopOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { isApiError } from '@/shared/api';
import {
  archiveAsset,
  deleteAsset,
  deprecateAsset,
  getAsset,
  restoreAsset,
  updateAsset,
} from './api';
import type { AssetView, UpdateAssetRequest } from './types';

const { Title } = Typography;
const { TextArea } = Input;

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  DEPRECATED: 'orange',
  ARCHIVED: 'default',
};

export function AssetSettingsPage() {
  const { assetId } = useParams<{ assetId: string }>();
  const { t } = useTranslation();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useDocumentTitle(t('assets.settings.title'));

  const { data: asset, isLoading } = useQuery<AssetView>({
    queryKey: ['asset', assetId],
    queryFn: () => getAsset(assetId!),
    enabled: !!assetId,
  });

  const [form] = Form.useForm();

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    queryClient.invalidateQueries({ queryKey: ['assets'] });
  }, [queryClient, assetId]);

  const updateMutation = useMutation({
    mutationFn: (values: UpdateAssetRequest) =>
      updateAsset(assetId!, values),
    onSuccess: () => {
      message.success(t('common.save'));
      invalidate();
    },
    onError: (err: unknown) => {
      message.error(isApiError(err) ? err.message : t('common.operationFailed'));
    },
  });

  const deprecateMutation = useMutation({
    mutationFn: () => deprecateAsset(assetId!),
    onSuccess: () => {
      message.success(t('assets.deprecated'));
      invalidate();
    },
    onError: (err: unknown) => {
      message.error(isApiError(err) ? err.message : t('common.operationFailed'));
    },
  });

  const archiveMutation = useMutation({
    mutationFn: () => archiveAsset(assetId!),
    onSuccess: () => {
      message.success(t('assets.archived'));
      invalidate();
    },
    onError: (err: unknown) => {
      message.error(isApiError(err) ? err.message : t('common.operationFailed'));
    },
  });

  const restoreMutation = useMutation({
    mutationFn: () => restoreAsset(assetId!),
    onSuccess: () => {
      message.success(t('assets.restored'));
      invalidate();
    },
    onError: (err: unknown) => {
      message.error(isApiError(err) ? err.message : t('common.operationFailed'));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteAsset(assetId!),
    onSuccess: () => {
      message.success(t('assets.deleted'));
      navigate('/assets');
    },
    onError: (err: unknown) => {
      message.error(isApiError(err) ? err.message : t('common.operationFailed'));
    },
  });

  if (isLoading) {
    return (
      <Flex justify="center" align="center" style={{ minHeight: 400 }}>
        <Spin size="large" />
      </Flex>
    );
  }

  if (!asset) {
    return null;
  }

  const isEditable = asset.status === 'ACTIVE' || asset.status === 'DEPRECATED';

  return (
    <Flex vertical gap={24} style={{ maxWidth: 800 }}>
      <Flex justify="space-between" align="center">
        <Title level={3}>{t('assets.settings.title')}</Title>
        <Button onClick={() => navigate(`/assets/${assetId}`)}>
          {t('common.back')}
        </Button>
      </Flex>

      <Card title={t('assets.settings.basicInfo')}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('assets.settings.assetId')}>{asset.assetId}</Descriptions.Item>
          <Descriptions.Item label={t('common.type')}>{asset.type}</Descriptions.Item>
          <Descriptions.Item label={t('assets.create.namespace')}>{asset.namespace}</Descriptions.Item>
          <Descriptions.Item label={t('assets.columns.name')}>{asset.name}</Descriptions.Item>
          <Descriptions.Item label={t('assets.columns.status')}>
            <Badge color={STATUS_COLOR[asset.status] ?? 'default'} text={asset.status} />
          </Descriptions.Item>
          <Descriptions.Item label={t('assets.columns.visibility')}>{asset.visibility}</Descriptions.Item>
        </Descriptions>
      </Card>

      {isEditable && (
        <Card title={t('assets.settings.editMetadata')}>
          <Form
            form={form}
            layout="vertical"
            initialValues={{
              displayName: asset.displayName,
              description: asset.description,
              visibility: asset.visibility,
            }}
            onFinish={(values) => updateMutation.mutate(values)}
          >
            <Form.Item label={t('assets.create.displayNameLabel')} name="displayName">
              <Input />
            </Form.Item>
            <Form.Item label={t('common.description')} name="description">
              <TextArea rows={3} />
            </Form.Item>
            <Form.Item label={t('assets.create.visibility')} name="visibility">
              <Select
                options={[
                  { value: 'PUBLIC', label: t('assets.create.public') },
                  { value: 'INTERNAL', label: t('assets.create.internal') },
                  { value: 'PRIVATE', label: t('assets.create.private') },
                ]}
              />
            </Form.Item>
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={updateMutation.isPending}
              >
                {t('common.save')}
              </Button>
            </Form.Item>
          </Form>
        </Card>
      )}

      <Card title={t('assets.settings.lifecycle')}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {asset.status === 'ACTIVE' && (
            <>
              <Flex gap={12}>
                <Popconfirm
                  title={t('assets.confirmDeprecate')}
                  icon={<ExclamationCircleOutlined />}
                  onConfirm={() => deprecateMutation.mutate()}
                >
                  <Button icon={<StopOutlined />} loading={deprecateMutation.isPending}>
                    {t('assets.deprecate')}
                  </Button>
                </Popconfirm>
                <Popconfirm
                  title={t('assets.confirmArchive')}
                  icon={<InboxOutlined />}
                  onConfirm={() => archiveMutation.mutate()}
                >
                  <Button icon={<InboxOutlined />} loading={archiveMutation.isPending}>
                    {t('assets.archive')}
                  </Button>
                </Popconfirm>
              </Flex>
            </>
          )}
          {(asset.status === 'DEPRECATED' || asset.status === 'ARCHIVED') && (
            <Popconfirm
              title={t('assets.confirmRestore')}
              icon={<UndoOutlined />}
              onConfirm={() => restoreMutation.mutate()}
            >
              <Button type="primary" icon={<UndoOutlined />} loading={restoreMutation.isPending}>
                {t('assets.restore')}
              </Button>
            </Popconfirm>
          )}
          {asset.status === 'DEPRECATED' && (
            <Popconfirm
              title={t('assets.confirmArchive')}
              icon={<InboxOutlined />}
              onConfirm={() => archiveMutation.mutate()}
            >
              <Button icon={<InboxOutlined />} loading={archiveMutation.isPending}>
                {t('assets.archive')}
              </Button>
            </Popconfirm>
          )}
          <Divider />
          <Popconfirm
            title={t('assets.confirmDelete')}
            icon={<DeleteOutlined style={{ color: '#ff4d4f' }} />}
            okType="danger"
            onConfirm={() => deleteMutation.mutate()}
          >
            <Button danger icon={<DeleteOutlined />} loading={deleteMutation.isPending}>
              {t('common.delete')}
            </Button>
          </Popconfirm>
        </Space>
      </Card>
    </Flex>
  );
}
