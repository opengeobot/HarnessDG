/**
 * 功能: 创建资产弹窗。元数据表单 + 类型相关字段，提交后经统一 client 登记资产。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Form, Input, Modal, Select } from 'antd';
import { useTranslation } from 'react-i18next';
import { isApiError } from '@/shared/api';
import { ControlledSelect, type SelectOption } from '@/shared/components/ControlledSelect';
import { createAsset } from './api';
import type { AssetType, CreateAssetRequest, Visibility } from './types';

interface CreateAssetModalProps {
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  type: AssetType;
  organizationId?: string;
  projectId?: string;
  namespace: string;
  name: string;
  displayName?: string;
  description?: string;
  visibility: Visibility;
  owners?: string[];
  tags?: string[];
  tagIds?: string[];
  license?: string;
  framework?: string;
  task?: string;
  architecture?: string;
  format?: string;
  modality?: string;
}

export function CreateAssetModal({ open, onClose }: CreateAssetModalProps) {
  const [form] = Form.useForm<FormValues>();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { t } = useTranslation();
  const assetType = Form.useWatch('type', form);
  const orgId = Form.useWatch('organizationId', form);

  const mutation = useMutation({
    mutationFn: (payload: CreateAssetRequest) => createAsset(payload),
    onSuccess: (asset) => {
      message.success(`${t('assets.create.title')} ${asset.namespace}/${asset.name}`);
      void queryClient.invalidateQueries({ queryKey: ['assets'] });
      form.resetFields();
      onClose();
    },
    onError: (error) => {
      const text = isApiError(error) ? error.message : t('discussion.createFailed');
      message.error(text);
    },
  });

  const handleOk = () => {
    form
      .validateFields()
      .then((values) => {
        const payload: CreateAssetRequest = {
          type: values.type,
          organizationId: values.organizationId || undefined,
          projectId: values.projectId || undefined,
          namespace: values.namespace,
          name: values.name,
          displayName: values.displayName,
          description: values.description,
          visibility: values.visibility,
          owners: values.owners,
          tags: values.tags,
          tagIds: values.tagIds,
          license: values.license,
          model:
            values.type === 'MODEL'
              ? {
                  framework: values.framework,
                  task: values.task,
                  architecture: values.architecture,
                }
              : undefined,
          dataset:
            values.type === 'DATASET'
              ? { format: values.format, modality: values.modality }
              : undefined,
        };
        mutation.mutate(payload);
      })
      .catch(() => undefined);
  };

  return (
    <Modal
      title={t('assets.create.title')}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={mutation.isPending}
      okText={t('common.create')}
      cancelText={t('common.cancel')}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ type: 'MODEL', visibility: 'INTERNAL' }}
        preserve={false}
      >
        <Form.Item name="type" label={t('common.type')} rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'MODEL', label: t('assets.model') },
              { value: 'DATASET', label: t('assets.dataset') },
            ]}
          />
        </Form.Item>
        <Form.Item name="organizationId" label={t('assets.create.org')}>
          <ControlledSelect
            apiUrl="/system/organizations"
            queryKey="organizations"
            extractOptions={(data) =>
              (data as Array<{ organizationId: string; name: string }>).map(
                (o): SelectOption => ({ value: o.organizationId, label: `${o.name} (${o.organizationId})` }),
              )
            }
            placeholder={t('assets.create.orgPlaceholder')}
            allowClear
          />
        </Form.Item>
        <Form.Item name="projectId" label={t('assets.create.project')}>
          <ControlledSelect
            apiUrl={`/system/organizations/${orgId ?? ''}/projects`}
            queryKey={['projects', orgId ?? '']}
            enabled={!!orgId}
            extractOptions={(data) =>
              (data as Array<{ projectId: string; name: string }>).map(
                (p): SelectOption => ({ value: p.projectId, label: `${p.name} (${p.projectId})` }),
              )
            }
            placeholder={t('assets.create.projectPlaceholder')}
            allowClear
            disabled={!orgId}
          />
        </Form.Item>
        <Form.Item
          name="namespace"
          label={t('assets.create.namespace')}
          rules={[
            { required: true, message: t('assets.create.namespaceRequired') },
            {
              pattern: /^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/,
              message: t('assets.create.namespacePattern'),
            },
          ]}
        >
          <Input placeholder="nlp" />
        </Form.Item>
        <Form.Item
          name="name"
          label={t('common.name')}
          rules={[
            { required: true, message: t('assets.create.nameRequired') },
            {
              pattern: /^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/,
              message: t('assets.create.namespacePattern'),
            },
          ]}
        >
          <Input placeholder="qwen-domain-7b" />
        </Form.Item>
        <Form.Item name="displayName" label={t('assets.create.displayNameLabel')}>
          <Input />
        </Form.Item>
        <Form.Item name="description" label={t('common.description')}>
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item name="visibility" label={t('assets.create.visibility')} rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'PRIVATE', label: t('assets.create.private') },
              { value: 'INTERNAL', label: t('assets.create.internal') },
              { value: 'PUBLIC', label: t('assets.create.public') },
            ]}
          />
        </Form.Item>
        <Form.Item name="owners" label={t('assets.create.ownerLabel')}>
          <ControlledSelect
            mode="multiple"
            apiUrl={`/system/organizations/${orgId ?? ''}/teams`}
            queryKey={['teams', orgId ?? '']}
            enabled={!!orgId}
            extractOptions={(data) =>
              (data as Array<{ teamId: string; name: string }>).map(
                (team): SelectOption => ({ value: team.teamId, label: `${team.name} (${team.teamId})` }),
              )
            }
            placeholder={t('assets.create.ownerPlaceholder')}
            disabled={!orgId}
          />
        </Form.Item>
        <Form.Item name="tagIds" label={t('assets.create.controlledTagIds')}>
          <ControlledSelect
            mode="multiple"
            apiUrl="/system/tags"
            queryKey="tags"
            extractOptions={(data) =>
              (data as Array<{ tagId: string; displayName: string; tagCode: string }>).map(
                (tag): SelectOption => ({ value: tag.tagId, label: `${tag.displayName} (${tag.tagCode})` }),
              )
            }
            placeholder={t('assets.create.tagPlaceholder')}
          />
        </Form.Item>
        <Form.Item name="license" label={t('assets.create.license')}>
          <ControlledSelect
            apiUrl="/system/dictionaries/license/items"
            queryKey="dict-license"
            extractOptions={(data) =>
              (data as Array<{ itemCode: string; i18nKey: string }>).map(
                (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
              )
            }
            placeholder={t('assets.create.licensePlaceholder')}
            allowClear
          />
        </Form.Item>
        {assetType === 'MODEL' ? (
          <>
            <Form.Item name="framework" label={t('assets.detail.framework')}>
              <ControlledSelect
                apiUrl="/system/dictionaries/framework/items"
                queryKey="dict-framework"
                extractOptions={(data) =>
                  (data as Array<{ itemCode: string }>).map(
                    (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                  )
                }
                placeholder={t('assets.create.frameworkPlaceholder')}
                allowClear
              />
            </Form.Item>
            <Form.Item name="task" label={t('assets.detail.task')}>
              <ControlledSelect
                apiUrl="/system/dictionaries/task/items"
                queryKey="dict-task"
                extractOptions={(data) =>
                  (data as Array<{ itemCode: string }>).map(
                    (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                  )
                }
                placeholder={t('assets.create.taskPlaceholder')}
                allowClear
              />
            </Form.Item>
            <Form.Item name="architecture" label={t('assets.detail.architecture')}>
              <Input placeholder="decoder-only" />
            </Form.Item>
          </>
        ) : (
          <>
            <Form.Item name="format" label={t('assets.create.dataFormat')}>
              <ControlledSelect
                apiUrl="/system/dictionaries/format/items"
                queryKey="dict-format"
                extractOptions={(data) =>
                  (data as Array<{ itemCode: string }>).map(
                    (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                  )
                }
                placeholder={t('assets.create.formatPlaceholder')}
                allowClear
              />
            </Form.Item>
            <Form.Item name="modality" label={t('assets.detail.modality')}>
              <ControlledSelect
                apiUrl="/system/dictionaries/modality/items"
                queryKey="dict-modality"
                extractOptions={(data) =>
                  (data as Array<{ itemCode: string }>).map(
                    (item): SelectOption => ({ value: item.itemCode, label: item.itemCode }),
                  )
                }
                placeholder={t('assets.create.modalityPlaceholder')}
                allowClear
              />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
}
