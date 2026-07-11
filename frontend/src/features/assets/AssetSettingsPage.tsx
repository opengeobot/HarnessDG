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
  Skeleton,
  Space,
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
import { ControlledSelect, type SelectOption } from '@/shared/components/ControlledSelect';
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
  const [profileForm] = Form.useForm();
  const [deprecateForm] = Form.useForm();

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    queryClient.invalidateQueries({ queryKey: ['assets'] });
  }, [queryClient, assetId]);

  const updateMutation = useMutation({
    mutationFn: (values: UpdateAssetRequest) =>
      updateAsset(assetId!, { ...values, expectedVersion: asset?.rowVersion ?? 0 }),
    onSuccess: () => {
      message.success(t('common.save'));
      invalidate();
    },
    onError: (err: unknown) => {
      if (isApiError(err) && err.httpStatus === 409) {
        message.warning(t('assets.conflictReload'));
        queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
      } else {
        message.error(isApiError(err) ? err.message : t('common.operationFailed'));
      }
    },
  });

  const deprecateMutation = useMutation({
    mutationFn: (body?: {
      deprecationReason?: string;
      deprecationNote?: string;
      replacementAssetId?: string;
    }) => deprecateAsset(assetId!, body),
    onSuccess: () => {
      message.success(t('assets.deprecated'));
      invalidate();
    },
    onError: (err: unknown) => {
      if (isApiError(err) && err.httpStatus === 409) {
        message.warning(t('assets.conflictReload'));
        queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
      } else {
        message.error(isApiError(err) ? err.message : t('common.operationFailed'));
      }
    },
  });

  const archiveMutation = useMutation({
    mutationFn: () => archiveAsset(assetId!),
    onSuccess: () => {
      message.success(t('assets.archived'));
      invalidate();
    },
    onError: (err: unknown) => {
      if (isApiError(err) && err.httpStatus === 409) {
        message.warning(t('assets.conflictReload'));
        queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
      } else {
        message.error(isApiError(err) ? err.message : t('common.operationFailed'));
      }
    },
  });

  const restoreMutation = useMutation({
    mutationFn: () => restoreAsset(assetId!),
    onSuccess: () => {
      message.success(t('assets.restored'));
      invalidate();
    },
    onError: (err: unknown) => {
      if (isApiError(err) && err.httpStatus === 409) {
        message.warning(t('assets.conflictReload'));
        queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
      } else {
        message.error(isApiError(err) ? err.message : t('common.operationFailed'));
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteAsset(assetId!),
    onSuccess: () => {
      message.success(t('assets.deleted'));
      navigate('/assets');
    },
    onError: (err: unknown) => {
      if (isApiError(err) && err.httpStatus === 409) {
        message.warning(t('assets.conflictReload'));
        queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
      } else {
        message.error(isApiError(err) ? err.message : t('common.operationFailed'));
      }
    },
  });

  if (isLoading) {
    return (
      <Flex vertical gap={24} style={{ maxWidth: 800 }}>
        <Skeleton.Input active size="large" style={{ width: 200 }} />
        <Skeleton active paragraph={{ rows: 4 }} />
        <Skeleton active paragraph={{ rows: 6 }} />
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
        <Space>
          <Button onClick={() => navigate(`/assets/${assetId}/access`)}>
            {t('assets.access.title')}
          </Button>
          <Button onClick={() => navigate(`/assets/${assetId}`)}>
            {t('common.back')}
          </Button>
        </Space>
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

      {isEditable && (
        <Card title={t('assets.settings.editProfile')}>
          <Form
            form={profileForm}
            layout="vertical"
            key={`${asset.assetId}-${asset.rowVersion}`}
            initialValues={
              asset.type === 'MODEL'
                ? {
                    framework: asset.model?.framework ?? undefined,
                    task: asset.model?.task ?? undefined,
                    architecture: asset.model?.architecture ?? undefined,
                    parameterScale: asset.model?.parameterScale ?? undefined,
                    precision: asset.model?.precision ?? undefined,
                    weightFormat: asset.model?.weightFormat ?? undefined,
                    runtime: asset.model?.runtime ?? undefined,
                    sensitivityCode: asset.model?.sensitivityCode ?? undefined,
                  }
                : {
                    format: asset.dataset?.format ?? undefined,
                    modality: asset.dataset?.modality ?? undefined,
                    taskCodes: asset.dataset?.taskCodes ?? undefined,
                    modalityCodes: asset.dataset?.modalityCodes ?? undefined,
                    formatCodes: asset.dataset?.formatCodes ?? undefined,
                    languageCodes: asset.dataset?.languageCodes ?? undefined,
                    sensitivityCode: asset.dataset?.sensitivityCode ?? undefined,
                    sampleCount: asset.dataset?.sampleCount ?? undefined,
                    totalBytes: asset.dataset?.totalBytes ?? undefined,
                  }
            }
            onFinish={(values) =>
              updateMutation.mutate({
                expectedVersion: asset.rowVersion,
                ...(asset.type === 'MODEL'
                  ? {
                      model: {
                        framework: values.framework,
                        task: values.task,
                        architecture: values.architecture,
                        parameterScale: values.parameterScale,
                        precision: values.precision,
                        weightFormat: values.weightFormat,
                        runtime: values.runtime,
                        sensitivityCode: values.sensitivityCode,
                      },
                    }
                  : {
                      dataset: {
                        format: values.format,
                        modality: values.modality,
                        taskCodes: values.taskCodes,
                        modalityCodes: values.modalityCodes,
                        formatCodes: values.formatCodes,
                        languageCodes: values.languageCodes,
                        sensitivityCode: values.sensitivityCode,
                        sampleCount: values.sampleCount,
                        totalBytes: values.totalBytes,
                      },
                    }),
              })
            }
          >
            {asset.type === 'MODEL' ? (
              <>
                <Form.Item name="framework" label={t('assets.detail.framework')}>
                  <ControlledSelect
                    apiUrl="/system/dictionaries/model_framework/items"
                    queryKey="dict-model-framework-settings"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                    allowClear
                  />
                </Form.Item>
                <Form.Item name="task" label={t('assets.detail.task')}>
                  <ControlledSelect
                    apiUrl="/system/dictionaries/model_task/items"
                    queryKey="dict-model-task-settings"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                    allowClear
                  />
                </Form.Item>
                <Form.Item name="architecture" label={t('assets.detail.architecture')}>
                  <Input />
                </Form.Item>
                <Form.Item name="parameterScale" label={t('assets.detail.parameterScale')}>
                  <Input />
                </Form.Item>
                <Form.Item name="precision" label={t('assets.detail.precision')}>
                  <Input />
                </Form.Item>
                <Form.Item name="weightFormat" label={t('assets.detail.weightFormat')}>
                  <Input />
                </Form.Item>
                <Form.Item name="runtime" label={t('assets.detail.runtime')}>
                  <Input />
                </Form.Item>
                <Form.Item name="sensitivityCode" label={t('assets.detail.sensitivity')}>
                  <ControlledSelect
                    apiUrl="/system/dictionaries/sensitivity_level/items"
                    queryKey="dict-sensitivity-settings"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                    allowClear
                  />
                </Form.Item>
              </>
            ) : (
              <>
                <Form.Item name="format" label={t('assets.create.dataFormat')}>
                  <ControlledSelect
                    apiUrl="/system/dictionaries/dataset_format/items"
                    queryKey="dict-dataset-format-settings"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                    allowClear
                  />
                </Form.Item>
                <Form.Item name="modality" label={t('assets.detail.modality')}>
                  <ControlledSelect
                    apiUrl="/system/dictionaries/dataset_modality/items"
                    queryKey="dict-dataset-modality-settings"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                    allowClear
                  />
                </Form.Item>
                <Form.Item name="taskCodes" label={t('assets.detail.taskCodes')}>
                  <ControlledSelect
                    mode="multiple"
                    apiUrl="/system/dictionaries/model_task/items"
                    queryKey="dict-model-task-settings-multi"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                  />
                </Form.Item>
                <Form.Item name="modalityCodes" label={t('assets.detail.modalityCodes')}>
                  <ControlledSelect
                    mode="multiple"
                    apiUrl="/system/dictionaries/dataset_modality/items"
                    queryKey="dict-dataset-modality-settings-multi"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                  />
                </Form.Item>
                <Form.Item name="formatCodes" label={t('assets.detail.formatCodes')}>
                  <ControlledSelect
                    mode="multiple"
                    apiUrl="/system/dictionaries/dataset_format/items"
                    queryKey="dict-dataset-format-settings-multi"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                  />
                </Form.Item>
                <Form.Item name="languageCodes" label={t('assets.detail.languageCodes')}>
                  <ControlledSelect
                    mode="multiple"
                    apiUrl="/system/dictionaries/language/items"
                    queryKey="dict-language-settings-multi"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                  />
                </Form.Item>
                <Form.Item name="sampleCount" label={t('assets.detail.sampleCount')}>
                  <Input type="number" />
                </Form.Item>
                <Form.Item name="totalBytes" label={t('assets.detail.totalBytes')}>
                  <Input type="number" />
                </Form.Item>
                <Form.Item name="sensitivityCode" label={t('assets.detail.sensitivity')}>
                  <ControlledSelect
                    apiUrl="/system/dictionaries/sensitivity_level/items"
                    queryKey="dict-sensitivity-dataset-settings"
                    extractOptions={(data) =>
                      (data as Array<{ itemCode: string }>).map(
                        (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                      )
                    }
                    allowClear
                  />
                </Form.Item>
              </>
            )}
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={updateMutation.isPending}>
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
                  onConfirm={() => {
                    const values = deprecateForm.getFieldsValue();
                    deprecateMutation.mutate({
                      deprecationReason: values.deprecationReason || undefined,
                      deprecationNote: values.deprecationNote || undefined,
                      replacementAssetId: values.replacementAssetId || undefined,
                    });
                  }}
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
              <Form form={deprecateForm} layout="vertical" size="small">
                <Form.Item name="deprecationReason" label={t('assets.deprecation.reason')}>
                  <Select
                    placeholder={t('assets.deprecation.reasonPlaceholder')}
                    allowClear
                    options={[
                      { value: 'REPLACED', label: t('assets.deprecation.REPLACED') },
                      { value: 'OUTDATED', label: t('assets.deprecation.OUTDATED') },
                      { value: 'SECURITY_ISSUE', label: t('assets.deprecation.SECURITY_ISSUE') },
                      { value: 'UNSUPPORTED', label: t('assets.deprecation.UNSUPPORTED') },
                      { value: 'MERGED', label: t('assets.deprecation.MERGED') },
                    ]}
                  />
                </Form.Item>
                <Form.Item name="deprecationNote" label={t('assets.deprecation.note')}>
                  <TextArea rows={2} placeholder={t('assets.deprecation.notePlaceholder')} />
                </Form.Item>
                <Form.Item name="replacementAssetId" label={t('assets.deprecation.replacement')}>
                  <Input placeholder="ast_xxx" />
                </Form.Item>
              </Form>
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
