/**
 * 功能: 角色管理页面。角色列表 + 创建/编辑/删除；权限集合以权限清单多选。
 *       内置(SYSTEM)角色不可编辑/删除，后端会拒绝，前端相应禁用并提示。
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
  createRole,
  deleteRole,
  listPermissions,
  listRoles,
  updateRole,
} from '../api';
import type { CreateRoleRequest, RoleView } from '../types';

interface RoleFormValues {
  roleCode: string;
  roleName: string;
  permissionCodes: string[];
}

export function RolesPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.roles.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<RoleView | null>(null);
  const [form] = Form.useForm<RoleFormValues>();

  const rolesQuery = useQuery({ queryKey: ['admin', 'roles'], queryFn: listRoles });
  const permsQuery = useQuery({ queryKey: ['admin', 'permissions'], queryFn: listPermissions });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const saveMutation = useMutation({
    mutationFn: (values: RoleFormValues) => {
      if (editing) {
        return updateRole(editing.roleId, {
          roleName: values.roleName,
          permissionCodes: values.permissionCodes,
          expectedVersion: editing.version,
        });
      }
      return createRole(values as CreateRoleRequest);
    },
    onSuccess: () => {
      message.success(editing ? t('admin.roles.roleUpdated') : t('admin.roles.roleCreated'));
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      void invalidate();
    },
    onError,
  });

  const deleteMutation = useMutation({
    mutationFn: (roleId: string) => deleteRole(roleId),
    onSuccess: () => {
      message.success(t('admin.roles.roleDeleted'));
      void invalidate();
    },
    onError,
  });

  const permissionOptions = (permsQuery.data ?? []).map((perm) => ({
    value: perm.permissionCode,
    label: perm.permissionCode,
  }));

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (role: RoleView) => {
    setEditing(role);
    form.setFieldsValue({
      roleCode: role.roleCode,
      roleName: role.roleName,
      permissionCodes: role.permissionCodes,
    });
    setModalOpen(true);
  };

  const columns: ColumnsType<RoleView> = [
    {
      title: t('admin.roles.role'),
      key: 'role',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.roleName}</Typography.Text>
          <Typography.Text type="secondary" code>
            {record.roleCode}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: t('admin.roles.roleType'),
      dataIndex: 'roleType',
      key: 'roleType',
      render: (type: string) => (
        <Tag color={type === 'SYSTEM' ? 'gold' : 'blue'}>{type === 'SYSTEM' ? t('admin.roles.builtIn') : t('admin.roles.custom')}</Tag>
      ),
    },
    {
      title: t('admin.roles.permissions'),
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
      title: t('common.action'),
      key: 'action',
      render: (_, record) => {
        const isSystem = record.roleType === 'SYSTEM';
        return (
          <Space size="small">
            <Button type="link" size="small" disabled={isSystem} onClick={() => openEdit(record)}>
              {t('common.edit')}
            </Button>
            <Popconfirm
              title={t('admin.roles.confirmDelete')}
              disabled={isSystem}
              onConfirm={() => deleteMutation.mutate(record.roleId)}
            >
              <Button type="link" size="small" danger disabled={isSystem}>
                {t('common.delete')}
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.roles.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => rolesQuery.refetch()}>{t('common.refresh')}</Button>
          <Button type="primary" onClick={openCreate}>
            {t('admin.roles.createRole')}
          </Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={rolesQuery.isLoading}
        isError={rolesQuery.isError}
        error={rolesQuery.error}
        onRetry={() => rolesQuery.refetch()}
      >
        <Table<RoleView> rowKey="roleId" columns={columns} dataSource={rolesQuery.data ?? []} />
      </QueryBoundary>

      <Modal
        title={editing ? t('admin.roles.editRole') : t('admin.roles.createRole')}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item name="roleCode" label={t('admin.roles.roleCode')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.roles.roleCodePlaceholder')} disabled={editing !== null} />
          </Form.Item>
          <Form.Item name="roleName" label={t('admin.roles.roleName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="permissionCodes" label={t('admin.roles.permissionSet')} rules={[{ required: true }]}>
            <Select
              mode="multiple"
              placeholder={t('admin.roles.selectPermissionCodes')}
              options={permissionOptions}
              loading={permsQuery.isLoading}
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
