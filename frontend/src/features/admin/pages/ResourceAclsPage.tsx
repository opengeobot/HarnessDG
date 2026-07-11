/**
 * 功能: 资源 ACL 管理页面。列表 + 创建模态 + 删除。
 * 时间: 2026-07-11
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
  Popconfirm,
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
import {
  createResourceAcl,
  deleteResourceAcl,
  listPermissions,
  listResourceAcls,
} from '../api';
import type { CreateResourceAclRequest, ResourceAclView } from '../types';

interface AclFormValues {
  principalId: string;
  resourceType: string;
  resourceId: string;
  permissionCodes: string[];
}

export function ResourceAclsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.resourceAcls.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<AclFormValues>();

  const aclsQuery = useQuery({ queryKey: ['admin', 'resource-acls'], queryFn: listResourceAcls });
  const permsQuery = useQuery({ queryKey: ['admin', 'permissions'], queryFn: listPermissions });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'resource-acls'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (payload: CreateResourceAclRequest) => createResourceAcl(payload),
    onSuccess: () => {
      message.success(t('admin.resourceAcls.created'));
      setModalOpen(false);
      form.resetFields();
      void invalidate();
    },
    onError,
  });

  const deleteMutation = useMutation({
    mutationFn: (aclId: string) => deleteResourceAcl(aclId),
    onSuccess: () => {
      message.success(t('admin.resourceAcls.deleted'));
      void invalidate();
    },
    onError,
  });

  const permissionOptions = (permsQuery.data ?? []).map((perm) => ({
    value: perm.permissionCode,
    label: perm.permissionCode,
  }));

  const columns: ColumnsType<ResourceAclView> = [
    { title: t('admin.resourceAcls.aclId'), dataIndex: 'aclId', key: 'aclId' },
    { title: t('admin.resourceAcls.principalId'), dataIndex: 'principalId', key: 'principalId' },
    { title: t('admin.resourceAcls.resourceType'), dataIndex: 'resourceType', key: 'resourceType' },
    { title: t('admin.resourceAcls.resourceId'), dataIndex: 'resourceId', key: 'resourceId' },
    {
      title: t('admin.resourceAcls.permissions'),
      dataIndex: 'permissionCodes',
      key: 'permissionCodes',
      render: (codes: string[]) => (
        <Space size={[0, 4]} wrap>
          {codes.map((code) => (
            <Tag key={code}>{code}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('admin.resourceAcls.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm title={t('admin.resourceAcls.confirmDelete')} onConfirm={() => deleteMutation.mutate(record.aclId)}>
          <Button type="link" size="small" danger>
            {t('common.delete')}
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.resourceAcls.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => aclsQuery.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={() => setModalOpen(true)}>
            {t('admin.resourceAcls.create')}
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={aclsQuery.isLoading}
        isError={aclsQuery.isError}
        error={aclsQuery.error}
        onRetry={() => aclsQuery.refetch()}
      >
        <Table<ResourceAclView> rowKey="aclId" columns={columns} dataSource={aclsQuery.data ?? []} />
      </QueryBoundary>

      <Modal
        title={t('admin.resourceAcls.create')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item name="principalId" label={t('admin.resourceAcls.principalId')} rules={[{ required: true }]}>
            <Input placeholder="prn_xxx" />
          </Form.Item>
          <Form.Item name="resourceType" label={t('admin.resourceAcls.resourceType')} rules={[{ required: true }]}>
            <Input placeholder="ASSET" />
          </Form.Item>
          <Form.Item name="resourceId" label={t('admin.resourceAcls.resourceId')} rules={[{ required: true }]}>
            <Input placeholder="ast_xxx" />
          </Form.Item>
          <Form.Item name="permissionCodes" label={t('admin.resourceAcls.permissions')} rules={[{ required: true }]}>
            <Select
              mode="multiple"
              options={permissionOptions}
              loading={permsQuery.isLoading}
              placeholder={t('admin.resourceAcls.selectPermissions')}
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
