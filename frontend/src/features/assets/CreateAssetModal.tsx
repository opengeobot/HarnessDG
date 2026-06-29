/**
 * 功能: 创建资产弹窗。元数据表单 + 类型相关字段，提交后经统一 client 登记资产。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Form, Input, Modal, Select } from 'antd';
import { isApiError } from '@/shared/api';
import { createAsset } from './api';
import type { AssetType, CreateAssetRequest, Visibility } from './types';

interface CreateAssetModalProps {
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  type: AssetType;
  namespace: string;
  name: string;
  displayName?: string;
  description?: string;
  visibility: Visibility;
  owners?: string[];
  tags?: string[];
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
  const assetType = Form.useWatch('type', form);

  const mutation = useMutation({
    mutationFn: (payload: CreateAssetRequest) => createAsset(payload),
    onSuccess: (asset) => {
      message.success(`已登记资产 ${asset.namespace}/${asset.name}`);
      void queryClient.invalidateQueries({ queryKey: ['assets'] });
      form.resetFields();
      onClose();
    },
    onError: (error) => {
      const text = isApiError(error) ? error.message : '创建失败';
      message.error(text);
    },
  });

  const handleOk = () => {
    form
      .validateFields()
      .then((values) => {
        const payload: CreateAssetRequest = {
          type: values.type,
          namespace: values.namespace,
          name: values.name,
          displayName: values.displayName,
          description: values.description,
          visibility: values.visibility,
          owners: values.owners,
          tags: values.tags,
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
      title="登记资产"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={mutation.isPending}
      okText="创建"
      cancelText="取消"
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ type: 'MODEL', visibility: 'INTERNAL' }}
        preserve={false}
      >
        <Form.Item name="type" label="类型" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'MODEL', label: '模型' },
              { value: 'DATASET', label: '数据集' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="namespace"
          label="命名空间"
          rules={[
            { required: true, message: '请输入命名空间' },
            {
              pattern: /^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/,
              message: '仅小写字母、数字与连字符',
            },
          ]}
        >
          <Input placeholder="nlp" />
        </Form.Item>
        <Form.Item
          name="name"
          label="名称"
          rules={[
            { required: true, message: '请输入名称' },
            {
              pattern: /^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/,
              message: '仅小写字母、数字与连字符',
            },
          ]}
        >
          <Input placeholder="qwen-domain-7b" />
        </Form.Item>
        <Form.Item name="displayName" label="展示名称">
          <Input placeholder="领域问答模型" />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} placeholder="资产卡片摘要" />
        </Form.Item>
        <Form.Item name="visibility" label="可见性" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'PRIVATE', label: '私有' },
              { value: 'INTERNAL', label: '内部' },
              { value: 'PUBLIC', label: '公开' },
            ]}
          />
        </Form.Item>
        <Form.Item name="owners" label="Owner">
          <Select mode="tags" placeholder="team-nlp" tokenSeparators={[',']} />
        </Form.Item>
        <Form.Item name="tags" label="标签">
          <Select mode="tags" placeholder="text-generation" tokenSeparators={[',']} />
        </Form.Item>
        <Form.Item name="license" label="许可证">
          <Input placeholder="Apache-2.0" />
        </Form.Item>
        {assetType === 'MODEL' ? (
          <>
            <Form.Item name="framework" label="框架">
              <Input placeholder="pytorch" />
            </Form.Item>
            <Form.Item name="task" label="任务">
              <Input placeholder="text-generation" />
            </Form.Item>
            <Form.Item name="architecture" label="架构">
              <Input placeholder="decoder-only" />
            </Form.Item>
          </>
        ) : (
          <>
            <Form.Item name="format" label="数据格式">
              <Input placeholder="parquet" />
            </Form.Item>
            <Form.Item name="modality" label="模态">
              <Input placeholder="image" />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
}
