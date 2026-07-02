/**
 * 功能: 字典管理页面。左侧字典类型列表，选中后右侧管理字典项（新建/停用/启用）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
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
  useDocumentTitle('字典管理');
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
    message.error(isApiError(error) ? error.message : '操作失败');

  const createMutation = useMutation({
    mutationFn: (payload: CreateDictionaryItemRequest) =>
      createDictionaryItem(selected!, payload),
    onSuccess: () => {
      message.success('字典项已创建');
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
      message.success('状态已更新');
      void invalidateItems();
    },
    onError,
  });

  const columns: ColumnsType<DictionaryItemView> = [
    { title: '项编码', dataIndex: 'itemCode', key: 'itemCode' },
    { title: '文案 Key', dataIndex: 'i18nKey', key: 'i18nKey' },
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => toggleMutation.mutate(record)}>
          {record.status === 'ACTIVE' ? '停用' : '启用'}
        </Button>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        字典管理
      </Typography.Title>

      <Flex gap={16} align="stretch" wrap>
        <Card title="字典类型" style={{ width: 280 }}>
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
          title={selected ? `字典项 · ${selected}` : '字典项'}
          style={{ flex: 1, minWidth: 360 }}
          extra={
            <Button type="primary" size="small" disabled={!selected} onClick={() => setCreateOpen(true)}>
              新增字典项
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
            <Typography.Text type="secondary">请选择左侧字典类型。</Typography.Text>
          )}
        </Card>
      </Flex>

      <Modal
        title="新增字典项"
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
          <Form.Item name="itemCode" label="项编码" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="i18nKey" label="文案 Key" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
