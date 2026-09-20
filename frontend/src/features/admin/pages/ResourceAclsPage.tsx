/**
 * 功能: 资源 ACL 管理页面。列表 + 创建/删除；资源类型与权限均以受控来源取值
 *       （权限来自 /system/permissions 清单），不接受自由文本权限串。
 *       资源类型仅提供后端确有强制点的 ASSET，避免提交无强制语义的值。
 * 时间: 2026-09-18
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
import { ControlledSelect } from '@/shared/components/ControlledSelect';
import { useDocumentTitle } from '@/shared/hooks';
import { createResourceAcl, deleteResourceAcl, listResourceAcls } from '../api';
import type { CreateResourceAclRequest, PermissionView, ResourceAclView } from '../types';

/**
 * 允许创建 ACL 的资源类型。
 *
 * <p>仅列后端确有资源级强制点的取值：`AuthorizationService.isResourcePermitted` 的调用方
 * 按 `ASSET` 校验资产访问。未在此列出的取值即使能入库也不产生授权效果，
 * 故不作为可选项暴露，避免管理员创建“看似生效实则无效”的授权。
 */
const RESOURCE_TYPES = ['ASSET'] as const;

/** 提取权限下拉选项（受控来源：真实权限清单） */
function extractPermissionOptions(data: unknown) {
  const permissions = Array.isArray(data) ? (data as PermissionView[]) : [];
  return permissions.map((perm) => ({
    value: perm.permissionCode,
    label: perm.permissionCode,
  }));
}

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

  const aclsQuery = useQuery({
    queryKey: ['admin', 'resource-acls'],
    queryFn: listResourceAcls,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['admin', 'resource-acls'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (values: AclFormValues) => {
      const payload: CreateResourceAclRequest = {
        principalId: values.principalId.trim(),
        resourceType: values.resourceType,
        resourceId: values.resourceId.trim(),
        permissionCodes: values.permissionCodes,
      };
      return createResourceAcl(payload);
    },
    onSuccess: () => {
      message.success(t('admin.resourceAcls.aclCreated'));
      setModalOpen(false);
      form.resetFields();
      void invalidate();
    },
    onError,
  });

  const deleteMutation = useMutation({
    mutationFn: (aclId: string) => deleteResourceAcl(aclId),
    onSuccess: () => {
      message.success(t('admin.resourceAcls.aclDeleted'));
      void invalidate();
    },
    onError,
  });

  const columns: ColumnsType<ResourceAclView> = [
    {
      title: t('admin.resourceAcls.resourceType'),
      dataIndex: 'resourceType',
      key: 'resourceType',
      render: (value: string) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: t('admin.resourceAcls.resourceId'),
      dataIndex: 'resourceId',
      key: 'resourceId',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t('admin.resourceAcls.principalId'),
      dataIndex: 'principalId',
      key: 'principalId',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
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
      render: (value: string) => (value ? new Date(value).toLocaleString() : '-'),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title={t('admin.resourceAcls.confirmDelete')}
          onConfirm={() => deleteMutation.mutate(record.aclId)}
        >
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
          <Button
            type="primary"
            onClick={() => {
              form.resetFields();
              setModalOpen(true);
            }}
          >
            {t('admin.resourceAcls.createAcl')}
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={aclsQuery.isLoading}
        isError={aclsQuery.isError}
        error={aclsQuery.error}
        onRetry={() => aclsQuery.refetch()}
      >
        <Table<ResourceAclView>
          rowKey="aclId"
          columns={columns}
          dataSource={aclsQuery.data ?? []}
          locale={{ emptyText: t('admin.resourceAcls.empty') }}
        />
      </QueryBoundary>

      <Modal
        title={t('admin.resourceAcls.createAcl')}
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
          initialValues={{ resourceType: RESOURCE_TYPES[0] }}
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item
            name="principalId"
            label={t('admin.resourceAcls.principalId')}
            rules={[{ required: true }]}
          >
            <Input placeholder={t('admin.resourceAcls.principalIdPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="resourceType"
            label={t('admin.resourceAcls.resourceType')}
            rules={[{ required: true }]}
          >
            <Select
              options={RESOURCE_TYPES.map((value) => ({ value, label: value }))}
              placeholder={t('admin.resourceAcls.resourceTypePlaceholder')}
            />
          </Form.Item>
          <Form.Item
            name="resourceId"
            label={t('admin.resourceAcls.resourceId')}
            rules={[{ required: true }]}
          >
            <Input placeholder={t('admin.resourceAcls.resourceIdPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="permissionCodes"
            label={t('admin.resourceAcls.permissions')}
            rules={[
              { required: true, message: t('admin.resourceAcls.selectPermissionsRequired') },
            ]}
          >
            {/* 权限必须来自真实权限清单，禁止自由文本权限串绕过受控授予 */}
            <ControlledSelect
              mode="multiple"
              apiUrl="/system/permissions"
              queryKey={['admin', 'permissions']}
              extractOptions={extractPermissionOptions}
              placeholder={t('admin.resourceAcls.permissionPlaceholder')}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
