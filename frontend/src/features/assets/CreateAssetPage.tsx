/**
 * 功能: 创建资产独立页面（PAGE-AST-002）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Flex, Typography } from 'antd';
import { Form } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useDocumentTitle } from '@/shared/hooks';
import { isApiError } from '@/shared/api';
import { createAsset } from './api';
import { CreateAssetFormFields, buildCreateAssetPayload, type CreateAssetFormValues } from './CreateAssetForm';

export function CreateAssetPage() {
  const [form] = Form.useForm<CreateAssetFormValues>();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { t } = useTranslation();
  useDocumentTitle(t('assets.create.title'));

  const mutation = useMutation({
    mutationFn: createAsset,
    onSuccess: (asset) => {
      message.success(`${t('assets.create.title')} ${asset.namespace}/${asset.name}`);
      void queryClient.invalidateQueries({ queryKey: ['assets'] });
      navigate(`/assets/${asset.assetId}`);
    },
    onError: (error) => {
      const text = isApiError(error) ? error.message : t('discussion.createFailed');
      message.error(text);
    },
  });

  const handleSubmit = () => {
    form
      .validateFields()
      .then((values) => mutation.mutate(buildCreateAssetPayload(values)))
      .catch(() => undefined);
  };

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('assets.create.title')}
      </Typography.Title>
      <Card>
        <CreateAssetFormFields form={form} />
        <Flex gap={8} style={{ marginTop: 16 }}>
          <Button type="primary" loading={mutation.isPending} onClick={handleSubmit}>
            {t('common.create')}
          </Button>
          <Button onClick={() => navigate('/assets')}>{t('common.cancel')}</Button>
        </Flex>
      </Card>
    </Flex>
  );
}
