/**
 * 功能: 标签管理页面。受控标签列表 + 创建 + 编辑 + 启用/停用。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Flex,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { createTag, disableTag, enableTag, listTags, updateTag } from '../api';
import type { CreateTagRequest, TagScopeType, TagView } from '../types';

export function TagsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.tags.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<TagView | null>(null);
  const [createForm] = Form.useForm<CreateTagRequest>();
  const [editForm] = Form.useForm();

  const query = useQuery({ queryKey: ['admin', 'tags'], queryFn: () => listTags({}) });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'tags'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (payload: CreateTagRequest) => createTag(payload),
    onSuccess: () => {
      message.success(t('admin.tags.tagCreated'));
      setCreateOpen(false);
      createForm.resetFields();
      void invalidate();
    },
    onError,
  });

  const updateMutation = useMutation({
    mutationFn: (vars: { tagId: string; displayName: string; i18nKey: string; color?: string; version: number }) =>
      updateTag(vars.tagId, {
        displayName: vars.displayName,
        i18nKey: vars.i18nKey,
        color: vars.color || null,
        expectedVersion: vars.version,
      }),
    onSuccess: () => {
      message.success(t('admin.tags.tagUpdated'));
      setEditing(null);
      void invalidate();
    },
    onError,
  });

  const toggleMutation = useMutation({
    mutationFn: (tag: TagView) => (tag.status === 'ACTIVE' ? disableTag(tag.tagId) : enableTag(tag.tagId)),
    onSuccess: () => {
      message.success(t('admin.tags.statusUpdated'));
      void invalidate();
    },
    onError,
  });

  const columns: ColumnsType<TagView> = [
    {
      title: t('admin.tags.tag'),
      key: 'tag',
      render: (_, record) => (
        <Space>
          <Tag color={record.color ?? undefined}>{record.displayName}</Tag>
          <Typography.Text type="secondary" code>
            {record.tagCode}
          </Typography.Text>
        </Space>
      ),
    },
    { title: t('admin.tags.scopeType'), dataIndex: 'scopeType', key: 'scopeType' },
    { title: t('admin.tags.i18nKey'), dataIndex: 'i18nKey', key: 'i18nKey' },
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
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              editForm.setFieldsValue({
                displayName: record.displayName,
                i18nKey: record.i18nKey,
                color: record.color ?? undefined,
              });
            }}
          >
            {t('common.edit')}
          </Button>
          <Button type="link" size="small" onClick={() => toggleMutation.mutate(record)}>
            {record.status === 'ACTIVE' ? t('common.disable') : t('common.enable')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.tags.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            {t('admin.tags.createTag')}
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<TagView> rowKey="tagId" columns={columns} dataSource={query.data ?? []} />
      </QueryBoundary>

      <Modal
        title={t('admin.tags.createTag')}
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
          initialValues={{ scopeType: 'PLATFORM' as TagScopeType }}
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item name="scopeType" label={t('admin.tags.scopeType')} rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'PLATFORM', label: t('admin.tags.scopePlatform') },
                { value: 'ORGANIZATION', label: t('admin.tags.scopeOrganization') },
              ]}
            />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, cur) => prev.scopeType !== cur.scopeType}
          >
            {({ getFieldValue }) =>
              getFieldValue('scopeType') === 'ORGANIZATION' ? (
                <Form.Item name="scopeId" label={t('admin.tags.orgId')} rules={[{ required: true }]}>
                  <Input placeholder={t('admin.tags.orgIdPlaceholder')} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="tagCode" label={t('admin.tags.tagCode')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.tags.tagCodePlaceholder')} />
          </Form.Item>
          <Form.Item name="displayName" label={t('admin.tags.displayName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="i18nKey" label={t('admin.tags.i18nKey')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="color" label={t('admin.tags.color')}>
            <Input placeholder="#1677ff" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('admin.tags.editTag')}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => editForm.submit()}
        confirmLoading={updateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => {
            if (!editing) {
              return;
            }
            updateMutation.mutate({ tagId: editing.tagId, version: editing.version, ...values });
          }}
        >
          <Form.Item name="displayName" label={t('admin.tags.displayName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="i18nKey" label={t('admin.tags.i18nKey')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="color" label={t('admin.tags.color')}>
            <Input placeholder="#1677ff" />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
