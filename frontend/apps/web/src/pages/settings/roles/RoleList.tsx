/**
 * 功能：角色管理列表页（管理员）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Tag, Modal, Form, Input, message, Popconfirm, Drawer,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { roleApi } from '@/services';

interface RoleItem {
  id: number;
  code: string;
  name: Record<string, string>;
  description?: Record<string, string>;
  isSystem?: boolean;
  status: string;
  userCount?: number;
  permissionCount?: number;
  permissions?: PermissionRow[];
}

interface PermissionRow {
  id?: number;
  resourceType: string;
  resourceId: string;
  action: string;
  effect: string;
}

export default function RoleList() {
  const { t, i18n } = useTranslation(['settings', 'common']);
  const [data, setData] = useState<RoleItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [permOpen, setPermOpen] = useState(false);
  const [current, setCurrent] = useState<RoleItem | null>(null);
  const [perms, setPerms] = useState<PermissionRow[]>([]);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const locale = i18n.language || 'zh_CN';

  const load = async () => {
    setLoading(true);
    try {
      const res: any = await roleApi.listRoles();
      setData(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const localeLabel = (map?: Record<string, string>) =>
    map?.[locale] || map?.['zh_CN'] || map?.['en_US'] || '';

  const onCreate = async () => {
    const v = await createForm.validateFields();
    await roleApi.createRole({
      code: v.code,
      name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
      description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
      status: 'active',
    });
    message.success(t('common:action.save_success', 'Saved'));
    setCreateOpen(false);
    createForm.resetFields();
    load();
  };

  const onEdit = async () => {
    if (!current) return;
    const v = await editForm.validateFields();
    await roleApi.updateRole(current.id, {
      code: current.code,
      name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
      description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
      status: v.status || 'active',
    });
    message.success(t('common:action.save_success', 'Saved'));
    setEditOpen(false);
    load();
  };

  const onDelete = async (id: number) => {
    await roleApi.deleteRole(id);
    message.success(t('common:action.delete_success', 'Deleted'));
    load();
  };

  const openPermissions = async (role: RoleItem) => {
    setCurrent(role);
    const res: any = await roleApi.getRole(role.id);
    setPerms(res.data?.permissions || []);
    setPermOpen(true);
  };

  const addPerm = () => {
    setPerms(p => [...p, { resourceType: '', resourceId: '*', action: '*', effect: 'allow' }]);
  };

  const updatePerm = (idx: number, field: keyof PermissionRow, val: string) => {
    setPerms(p => p.map((row, i) => i === idx ? { ...row, [field]: val } : row));
  };

  const removePerm = (idx: number) => {
    setPerms(p => p.filter((_, i) => i !== idx));
  };

  const savePerms = async () => {
    if (!current) return;
    await roleApi.assignPermissions(current.id, perms);
    message.success(t('common:action.save_success', 'Saved'));
    setPermOpen(false);
    load();
  };

  const columns: ColumnsType<RoleItem> = [
    { title: t('role.code'), dataIndex: 'code', width: 160 },
    {
      title: t('role.name'),
      dataIndex: 'name',
      render: (name) => localeLabel(name),
    },
    {
      title: t('role.description'),
      dataIndex: 'description',
      render: (desc) => localeLabel(desc) || '-',
    },
    { title: t('role.user_count'), dataIndex: 'userCount', width: 100 },
    { title: t('role.permission_count'), dataIndex: 'permissionCount', width: 100 },
    {
      title: t('role.is_system'),
      dataIndex: 'isSystem',
      width: 110,
      render: (b: boolean) => b ? <Tag color="gold">{t('role.is_system')}</Tag> : '-',
    },
    {
      title: t('user.actions'),
      width: 260,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => {
            setCurrent(r);
            editForm.setFieldsValue({
              nameZh: r.name?.zh_CN,
              nameEn: r.name?.en_US,
              descZh: r.description?.zh_CN,
              descEn: r.description?.en_US,
              status: r.status,
            });
            setEditOpen(true);
          }}>{t('role.edit')}</Button>
          <Button size="small" onClick={() => openPermissions(r)}>{t('role.assign_permissions')}</Button>
          {!r.isSystem && (
            <Popconfirm title={t('user.delete_confirm')} onConfirm={() => onDelete(r.id)}>
              <Button size="small" danger>{t('common:action.delete', 'Delete')}</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        title={t('tabs.roles')}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => {
            createForm.resetFields();
            setCreateOpen(true);
          }}>{t('role.create')}</Button>
        }
      >
        <Table<RoleItem> rowKey="id" loading={loading} dataSource={data} columns={columns} scroll={{ x: 1100 }} pagination={false} />
      </Card>

      <Modal title={t('role.create')} open={createOpen} onOk={onCreate} onCancel={() => setCreateOpen(false)} destroyOnClose>
        <Form form={createForm} layout="vertical">
          <Form.Item name="code" label={t('role.code')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameZh" label={t('role.name') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameEn" label={t('role.name') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="descZh" label={t('role.description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('role.description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title={t('role.edit')} open={editOpen} onClose={() => setEditOpen(false)} width={520}
        extra={<Button type="primary" onClick={onEdit}>{t('common:action.save', 'Save')}</Button>}>
        <Form form={editForm} layout="vertical">
          <Form.Item name="nameZh" label={t('role.name') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameEn" label={t('role.name') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="descZh" label={t('role.description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('role.description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={t('role.assign_permissions') + (current ? ` - ${current.code}` : '')}
        open={permOpen}
        onClose={() => setPermOpen(false)}
        width={720}
        extra={
          <Space>
            <Button onClick={addPerm}>+ {t('role.permissions')}</Button>
            <Button type="primary" onClick={savePerms}>{t('common:action.save', 'Save')}</Button>
          </Space>
        }
      >
        <Table<PermissionRow>
          rowKey={(_, i) => String(i)}
          dataSource={perms}
          pagination={false}
          size="small"
          columns={[
            {
              title: 'resourceType',
              render: (_, r, i) => <Input size="small" value={r.resourceType} onChange={e => updatePerm(i, 'resourceType', e.target.value)} />,
            },
            {
              title: 'resourceId',
              render: (_, r, i) => <Input size="small" value={r.resourceId} onChange={e => updatePerm(i, 'resourceId', e.target.value)} />,
            },
            {
              title: 'action',
              render: (_, r, i) => <Input size="small" value={r.action} onChange={e => updatePerm(i, 'action', e.target.value)} />,
            },
            {
              title: 'effect',
              width: 100,
              render: (_, r, i) => <Input size="small" value={r.effect} onChange={e => updatePerm(i, 'effect', e.target.value)} />,
            },
            {
              title: '',
              width: 60,
              render: (_, __, i) => <Button size="small" danger onClick={() => removePerm(i)}>×</Button>,
            },
          ]}
        />
      </Drawer>
    </div>
  );
}
