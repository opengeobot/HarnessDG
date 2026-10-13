/**
 * 功能: 角色绑定管理页面。列表 + 创建模态 + 删除。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import {
  ControlledSelect,
  toPrincipalOptions,
  type SelectOption,
} from '@/shared/components/ControlledSelect';
import { searchAssets } from '@/features/assets/api';
import { useDocumentTitle } from '@/shared/hooks';
import {
  createRoleBinding,
  deleteRoleBinding,
  listRoleBindings,
  listRoles,
} from '../api';
import type { CreateRoleBindingRequest, RoleBindingView, ScopeType } from '../types';

const toOrganizationOptions = (data: unknown): SelectOption[] =>
  (data as Array<{ organizationId: string; name: string }>).map((o) => ({
    value: o.organizationId,
    label: `${o.name} (${o.organizationId})`,
  }));

interface BindingFormValues {
  principalId: string;
  roleId: string;
  scopeType: ScopeType;
  scopeId?: string;
}

const SCOPE_TYPES: ScopeType[] = ['PLATFORM', 'ORGANIZATION', 'PROJECT', 'ASSET'];

export function RoleBindingsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.roleBindings.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [assetSearch, setAssetSearch] = useState('');
  const [form] = Form.useForm<BindingFormValues>();
  const scopeType = Form.useWatch('scopeType', form);

  const bindingsQuery = useQuery({ queryKey: ['admin', 'role-bindings'], queryFn: listRoleBindings });
  const rolesQuery = useQuery({ queryKey: ['admin', 'roles'], queryFn: listRoles });

  // ASSET 作用域的资源检索：与资源 ACL 页相同的远程搜索范式
  const assetSearchQuery = useQuery({
    queryKey: ['role-binding-asset-search', assetSearch],
    queryFn: () => searchAssets({ keyword: assetSearch, limit: 20 }),
    enabled: modalOpen && scopeType === 'ASSET' && assetSearch.length >= 1,
  });

  const assetOptions = useMemo(
    () =>
      (assetSearchQuery.data?.items ?? []).map((item) => ({
        value: item.assetId,
        label: `${item.displayName ?? item.name} (${item.assetId})`,
      })),
    [assetSearchQuery.data?.items],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'role-bindings'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (payload: CreateRoleBindingRequest) => createRoleBinding(payload),
    onSuccess: () => {
      message.success(t('admin.roleBindings.created'));
      setModalOpen(false);
      form.resetFields();
      void invalidate();
    },
    onError,
  });

  const deleteMutation = useMutation({
    mutationFn: (bindingId: string) => deleteRoleBinding(bindingId),
    onSuccess: () => {
      message.success(t('admin.roleBindings.deleted'));
      void invalidate();
    },
    onError,
  });

  const roleOptions = (rolesQuery.data ?? []).map((role) => ({
    value: role.roleId,
    label: `${role.roleName} (${role.roleCode})`,
  }));

  const columns: ColumnsType<RoleBindingView> = [
    { title: t('admin.roleBindings.bindingId'), dataIndex: 'bindingId', key: 'bindingId' },
    { title: t('admin.roleBindings.principalId'), dataIndex: 'principalId', key: 'principalId' },
    { title: t('admin.roleBindings.roleId'), dataIndex: 'roleId', key: 'roleId' },
    { title: t('admin.roleBindings.scopeType'), dataIndex: 'scopeType', key: 'scopeType' },
    {
      title: t('admin.roleBindings.scopeId'),
      dataIndex: 'scopeId',
      key: 'scopeId',
      render: (v: string) => v || '-',
    },
    {
      title: t('admin.roleBindings.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm title={t('admin.roleBindings.confirmDelete')} onConfirm={() => deleteMutation.mutate(record.bindingId)}>
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
          {t('admin.roleBindings.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => bindingsQuery.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={() => setModalOpen(true)}>
            {t('admin.roleBindings.create')}
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={bindingsQuery.isLoading}
        isError={bindingsQuery.isError}
        error={bindingsQuery.error}
        onRetry={() => bindingsQuery.refetch()}
      >
        <Table<RoleBindingView>
          rowKey="bindingId"
          columns={columns}
          dataSource={bindingsQuery.data ?? []}
          locale={{ emptyText: <Empty description={t('admin.roleBindings.empty')} /> }}
        />
      </QueryBoundary>

      <Modal
        title={t('admin.roleBindings.create')}
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
          initialValues={{ scopeType: 'PLATFORM' }}
          onFinish={(values) =>
            createMutation.mutate({
              principalId: values.principalId,
              roleId: values.roleId,
              scopeType: values.scopeType,
              scopeId: values.scopeId || null,
            })
          }
        >
          <Form.Item
            name="principalId"
            label={t('admin.roleBindings.principalId')}
            rules={[{ required: true }]}
          >
            <ControlledSelect
              apiUrl="/system/principals"
              queryKey="principals-binding"
              extractOptions={toPrincipalOptions}
              placeholder={t('admin.roleBindings.principalIdPlaceholder')}
            />
          </Form.Item>
          <Form.Item name="roleId" label={t('admin.roleBindings.role')} rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={roleOptions}
              loading={rolesQuery.isLoading}
              placeholder={t('admin.roleBindings.selectRole')}
            />
          </Form.Item>
          <Form.Item name="scopeType" label={t('admin.roleBindings.scopeType')} rules={[{ required: true }]}>
            <Select
              options={SCOPE_TYPES.map((s) => ({ value: s, label: s }))}
              onChange={() => form.setFieldValue('scopeId', undefined)}
            />
          </Form.Item>
          {scopeType === 'ORGANIZATION' ? (
            <Form.Item name="scopeId" label={t('admin.roleBindings.scopeId')}>
              <ControlledSelect
                apiUrl="/system/organizations"
                queryKey="organizations-binding"
                extractOptions={toOrganizationOptions}
                placeholder={t('admin.roleBindings.scopeOrgPlaceholder')}
              />
            </Form.Item>
          ) : null}
          {scopeType === 'ASSET' ? (
            <Form.Item name="scopeId" label={t('admin.roleBindings.scopeId')}>
              <Select
                showSearch
                filterOption={false}
                onSearch={setAssetSearch}
                options={assetOptions}
                loading={assetSearchQuery.isFetching}
                notFoundContent={null}
                placeholder={t('admin.roleBindings.scopeAssetPlaceholder')}
              />
            </Form.Item>
          ) : null}
          {scopeType === 'PROJECT' ? (
            <Form.Item name="scopeId" label={t('admin.roleBindings.scopeId')}>
              <Input placeholder={t('admin.roleBindings.scopeProjectPlaceholder')} />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </Flex>
  );
}
