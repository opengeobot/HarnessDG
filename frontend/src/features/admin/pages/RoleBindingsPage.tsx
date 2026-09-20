/**
 * 功能: 角色绑定管理页面。列表 + 创建/删除；主体、角色与作用域类型均以受控选择器取值，
 *       不接受自由文本替换受控字段；作用域 ID 仅在非 PLATFORM 时必填（与后端契约一致）。
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
import { createRoleBinding, deleteRoleBinding, listRoleBindings } from '../api';
import type { CreateRoleBindingRequest, RoleBindingView, RoleView, ScopeType } from '../types';

/**
 * 角色绑定允许的作用域类型。
 *
 * <p>与后端 `RoleBindingApplicationService` 的校验一致：ASSET 作用域由资源 ACL 承载，
 * 故此处不提供 ASSET 选项，避免提交必被拒绝的值。
 */
const SCOPE_TYPES: ScopeType[] = ['PLATFORM', 'ORGANIZATION', 'PROJECT'];

/** 提取角色下拉选项（受控来源：真实角色清单，非自由输入） */
function extractRoleOptions(data: unknown) {
  const roles = Array.isArray(data) ? (data as RoleView[]) : [];
  return roles.map((role) => ({
    value: role.roleId,
    label: `${role.roleName} (${role.roleCode})`,
  }));
}

interface BindingFormValues {
  principalId: string;
  roleId: string;
  scopeType: ScopeType;
  scopeId?: string;
}

export function RoleBindingsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.roleBindings.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<BindingFormValues>();
  const scopeType = Form.useWatch('scopeType', form);

  const bindingsQuery = useQuery({
    queryKey: ['admin', 'role-bindings'],
    queryFn: listRoleBindings,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['admin', 'role-bindings'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const createMutation = useMutation({
    mutationFn: (values: BindingFormValues) => {
      const payload: CreateRoleBindingRequest = {
        principalId: values.principalId.trim(),
        roleId: values.roleId,
        scopeType: values.scopeType,
        // PLATFORM 作用域没有具体作用域 ID，提交 null 而非空串，与契约的 nullable 一致。
        scopeId: values.scopeType === 'PLATFORM' ? null : (values.scopeId ?? '').trim(),
      };
      return createRoleBinding(payload);
    },
    onSuccess: () => {
      message.success(t('admin.roleBindings.bindingCreated'));
      setModalOpen(false);
      form.resetFields();
      void invalidate();
    },
    onError,
  });

  const deleteMutation = useMutation({
    mutationFn: (bindingId: string) => deleteRoleBinding(bindingId),
    onSuccess: () => {
      message.success(t('admin.roleBindings.bindingDeleted'));
      void invalidate();
    },
    onError,
  });

  const columns: ColumnsType<RoleBindingView> = [
    {
      title: t('admin.roleBindings.principalId'),
      dataIndex: 'principalId',
      key: 'principalId',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t('admin.roleBindings.role'),
      dataIndex: 'roleId',
      key: 'roleId',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t('admin.roleBindings.scopeType'),
      dataIndex: 'scopeType',
      key: 'scopeType',
      render: (value: ScopeType) => <Tag color={value === 'PLATFORM' ? 'gold' : 'blue'}>{value}</Tag>,
    },
    {
      title: t('admin.roleBindings.scopeId'),
      dataIndex: 'scopeId',
      key: 'scopeId',
      render: (value: string | null | undefined) =>
        value ? <Typography.Text code>{value}</Typography.Text> : '-',
    },
    {
      title: t('admin.roleBindings.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (value: string) => (value ? new Date(value).toLocaleString() : '-'),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title={t('admin.roleBindings.confirmDelete')}
          onConfirm={() => deleteMutation.mutate(record.bindingId)}
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
          {t('admin.roleBindings.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => bindingsQuery.refetch()}>{t('common.refresh')}</Button>
          <Button
            type="primary"
            onClick={() => {
              form.resetFields();
              setModalOpen(true);
            }}
          >
            {t('admin.roleBindings.createBinding')}
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
          locale={{ emptyText: t('admin.roleBindings.empty') }}
        />
      </QueryBoundary>

      <Modal
        title={t('admin.roleBindings.createBinding')}
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
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item
            name="principalId"
            label={t('admin.roleBindings.principalId')}
            rules={[{ required: true }]}
          >
            <Input placeholder={t('admin.roleBindings.principalIdPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="roleId"
            label={t('admin.roleBindings.role')}
            rules={[{ required: true, message: t('admin.roleBindings.selectRoleRequired') }]}
          >
            {/* 角色必须来自真实角色清单，禁止自由文本输入角色 ID */}
            <ControlledSelect
              apiUrl="/system/roles"
              queryKey={['admin', 'roles']}
              extractOptions={extractRoleOptions}
              placeholder={t('admin.roleBindings.rolePlaceholder')}
            />
          </Form.Item>
          <Form.Item name="scopeType" label={t('admin.roleBindings.scopeType')} rules={[{ required: true }]}>
            <Select
              options={SCOPE_TYPES.map((value) => ({ value, label: value }))}
              onChange={(value: ScopeType) => {
                // 切到 PLATFORM 时清空作用域 ID，避免把上一个作用域的残留值提交上去。
                if (value === 'PLATFORM') {
                  form.setFieldValue('scopeId', undefined);
                }
              }}
            />
          </Form.Item>
          <Form.Item
            name="scopeId"
            label={t('admin.roleBindings.scopeId')}
            rules={[{ required: scopeType !== undefined && scopeType !== 'PLATFORM' }]}
          >
            <Input
              placeholder={t('admin.roleBindings.scopeIdPlaceholder')}
              disabled={scopeType === 'PLATFORM'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
