/**
 * 功能: 字典管理页面。左侧字典类型列表，选中后右侧管理字典项（新建/停用/启用）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Flex,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import {
  createDictionaryItem,
  listDictionaries,
  listDictionaryItems,
  updateDictionaryItem,
} from '../api';
import type { CreateDictionaryItemRequest, DictionaryItemView } from '../types';

export function DictionariesPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.dictionaries.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [selected, setSelected] = useState<string | undefined>();
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<CreateDictionaryItemRequest>();

  const typesQuery = useQuery({ queryKey: ['admin', 'dictionaries'], queryFn: listDictionaries });
  const itemsQuery = useQuery({
    queryKey: ['admin', 'dictionaries', selected, 'items'],
    queryFn: () => listDictionaryItems(selected!),
    enabled: Boolean(selected),
  });

  const invalidateItems = () =>
    queryClient.invalidateQueries({ queryKey: ['admin', 'dictionaries', selected, 'items'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (payload: CreateDictionaryItemRequest) =>
      createDictionaryItem(selected!, payload),
    onSuccess: () => {
      message.success(t('admin.dictionaries.itemCreated'));
      setCreateOpen(false);
      createForm.resetFields();
      void invalidateItems();
    },
    onError,
  });

  const toggleMutation = useMutation({
    mutationFn: (item: DictionaryItemView) =>
      updateDictionaryItem(item.dictCode, item.itemCode, {
        status: item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
        expectedVersion: item.version,
      }),
    onSuccess: () => {
      message.success(t('admin.dictionaries.statusUpdated'));
      void invalidateItems();
    },
    onError,
  });

  const columns: ColumnsType<DictionaryItemView> = [
    { title: t('admin.dictionaries.itemCode'), dataIndex: 'itemCode', key: 'itemCode' },
    { title: t('admin.dictionaries.i18nKey'), dataIndex: 'i18nKey', key: 'i18nKey' },
    { title: t('admin.dictionaries.sortOrder'), dataIndex: 'sortOrder', key: 'sortOrder' },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status}</Tag>
      ),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => toggleMutation.mutate(record)}>
          {record.status === 'ACTIVE' ? t('common.disable') : t('common.enable')}
        </Button>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('admin.dictionaries.title')}
      </Typography.Title>

      <Flex gap={16} align="stretch" wrap>
        <Card title={t('admin.dictionaries.dictType')} style={{ width: 280 }}>
          <QueryBoundary
            isLoading={typesQuery.isLoading}
            isError={typesQuery.isError}
            error={typesQuery.error}
            onRetry={() => typesQuery.refetch()}
          >
            <List
              dataSource={typesQuery.data ?? []}
              renderItem={(item) => (
                <List.Item
                  onClick={() => setSelected(item.dictCode)}
                  style={{
                    cursor: 'pointer',
                    background: item.dictCode === selected ? '#e6f4ff' : undefined,
                    padding: '8px 12px',
                  }}
                >
                  <Space direction="vertical" size={0}>
                    <Typography.Text strong>{item.dictCode}</Typography.Text>
                    <Typography.Text type="secondary">{item.i18nKey}</Typography.Text>
                  </Space>
                </List.Item>
              )}
            />
          </QueryBoundary>
        </Card>

        <Card
          title={selected ? t('admin.dictionaries.dictItemsOf', { code: selected }) : t('admin.dictionaries.dictItems')}
          style={{ flex: 1, minWidth: 360 }}
          extra={
            <Button type="primary" size="small" disabled={!selected} onClick={() => setCreateOpen(true)}>
              {t('admin.dictionaries.createItem')}
            </Button>
          }
        >
          {selected ? (
            <QueryBoundary
              isLoading={itemsQuery.isLoading}
              isError={itemsQuery.isError}
              error={itemsQuery.error}
              onRetry={() => itemsQuery.refetch()}
            >
              <Table<DictionaryItemView>
                rowKey="itemCode"
                size="small"
                columns={columns}
                dataSource={itemsQuery.data ?? []}
                pagination={false}
              />
            </QueryBoundary>
          ) : (
            <Typography.Text type="secondary">{t('admin.dictionaries.selectDictType')}</Typography.Text>
          )}
        </Card>
      </Flex>

      <Modal
        title={t('admin.dictionaries.createItem')}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          preserve={false}
          initialValues={{ sortOrder: 0 }}
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item name="itemCode" label={t('admin.dictionaries.itemCode')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="i18nKey" label={t('admin.dictionaries.i18nKey')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="sortOrder" label={t('admin.dictionaries.sortOrder')}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
