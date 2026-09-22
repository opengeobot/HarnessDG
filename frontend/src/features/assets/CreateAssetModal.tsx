/**
 * 功能: 创建资产弹窗。元数据表单 + 类型相关字段，提交后经统一 client 登记资产。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Form, Modal } from 'antd';
import { useTranslation } from 'react-i18next';
import { isApiError } from '@/shared/api';
import { createAsset } from './api';
import { CreateAssetFormFields, buildCreateAssetPayload, type CreateAssetFormValues } from './CreateAssetForm';

interface CreateAssetModalProps {
  open: boolean;
  onClose: () => void;
}

export function CreateAssetModal({ open, onClose }: CreateAssetModalProps) {
  const [form] = Form.useForm<CreateAssetFormValues>();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { t } = useTranslation();

  const mutation = useMutation({
    mutationFn: createAsset,
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
      .then((values) => mutation.mutate(buildCreateAssetPayload(values)))
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
      <CreateAssetFormFields form={form} />
    </Modal>
  );
}
