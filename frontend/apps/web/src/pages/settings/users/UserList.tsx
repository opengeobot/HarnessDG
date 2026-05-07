/**
 * 功能：用户管理列表页（管理员）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Input, Select, Tag, Modal, Form, message, Popconfirm, Drawer,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { userApi, roleApi } from '@/services';

interface UserItem {
  id: number;
  username: string;
  displayName?: string;
  email?: string;
  phone?: string;
  status: string;
  lastLoginAt?: string;
  createdAt?: string;
  roleCodes?: string[];
  roleIds?: number[];
}

interface RoleOption {
  id: number;
  code: string;
  name: Record<string, string>;
}

export default function UserList() {
  const { t, i18n } = useTranslation(['settings', 'common']);
  const [data, setData] = useState<UserItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);
  const [pwdOpen, setPwdOpen] = useState(false);
  const [current, setCurrent] = useState<UserItem | null>(null);

  const [roles, setRoles] = useState<RoleOption[]>([]);

  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [roleForm] = Form.useForm();
  const [pwdForm] = Form.useForm();

  const locale = i18n.language || 'zh_CN';

  const loadData = async () => {
    setLoading(true);
    try {
      const res: any = await userApi.listUsers({ keyword, status, page, pageSize } as any);
      setData(res.data?.items || []);
      setTotal(res.data?.total || 0);
    } finally {
      setLoading(false);
    }
  };

  const loadRoles = async () => {
    const res: any = await roleApi.listRoles();
    setRoles(res.data || []);
  };

  useEffect(() => {
    loadData();
  }, [page, pageSize]);

  useEffect(() => {
    loadRoles();
  }, []);

  const onSearch = () => {
    setPage(1);
    loadData();
  };

  const onCreate = async () => {
    const values = await createForm.validateFields();
    await userApi.createUser(values);
    message.success(t('common:action.save_success', 'Saved'));
    setCreateOpen(false);
    createForm.resetFields();
    loadData();
  };

  const onEdit = async () => {
    if (!current) return;
    const values = await editForm.validateFields();
    await userApi.updateUser(current.id, values);
    message.success(t('profile.update_success'));
    setEditOpen(false);
    loadData();
  };

  const onDelete = async (id: number) => {
    await userApi.deleteUser(id);
    message.success(t('common:action.delete_success', 'Deleted'));
    loadData();
  };

  const onToggleStatus = async (u: UserItem) => {
    const next = u.status === 'active' ? 'disabled' : 'active';
    await userApi.setUserStatus(u.id, next);
    loadData();
  };

  const onAssignRoles = async () => {
    if (!current) return;
    const values = await roleForm.validateFields();
    await userApi.assignRoles(current.id, values.roleIds || []);
    message.success(t('common:action.save_success', 'Saved'));
    setRoleOpen(false);
    loadData();
  };

  const onResetPassword = async () => {
    if (!current) return;
    const values = await pwdForm.validateFields();
    await userApi.resetPassword(current.id, values.newPassword);
    message.success(t('profile.password_changed'));
    setPwdOpen(false);
    pwdForm.resetFields();
  };

  const columns: ColumnsType<UserItem> = [
    { title: t('user.username'), dataIndex: 'username', width: 160 },
    { title: t('user.display_name'), dataIndex: 'displayName', width: 140 },
    { title: t('user.email'), dataIndex: 'email', width: 220 },
    { title: t('user.phone'), dataIndex: 'phone', width: 140 },
    {
      title: t('user.roles'),
      dataIndex: 'roleCodes',
      render: (codes: string[]) =>
        codes?.length ? codes.map(c => <Tag key={c} color="geekblue">{c}</Tag>) : '-',
    },
    {
      title: t('user.status'),
      dataIndex: 'status',
      width: 100,
      render: (s: string) => (
        <Tag color={s === 'active' ? 'green' : 'default'}>{s}</Tag>
      ),
    },
    { title: t('user.last_login_at'), dataIndex: 'lastLoginAt', width: 170 },
    {
      title: t('user.actions'),
      fixed: 'right',
      width: 340,
      render: (_, u) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => {
            setCurrent(u);
            editForm.setFieldsValue(u);
            setEditOpen(true);
          }}>{t('user.edit')}</Button>
          <Button size="small" onClick={() => {
            setCurrent(u);
            roleForm.setFieldsValue({ roleIds: u.roleIds || [] });
            setRoleOpen(true);
          }}>{t('user.assign_roles')}</Button>
          <Button size="small" onClick={() => {
            setCurrent(u);
            pwdForm.resetFields();
            setPwdOpen(true);
          }}>{t('user.reset_password')}</Button>
          <Button size="small" onClick={() => onToggleStatus(u)}>
            {u.status === 'active' ? t('user.disable') : t('user.enable')}
          </Button>
          <Popconfirm title={t('user.delete_confirm')} onConfirm={() => onDelete(u.id)}>
            <Button size="small" danger>{t('common:action.delete', 'Delete')}</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const localeLabel = (map?: Record<string, string>) =>
    map?.[locale] || map?.['zh_CN'] || map?.['en_US'] || '';

  return (
    <div>
      <Card
        title={t('title')}
        extra={
          <Space>
            <Input.Search
              allowClear
              placeholder={t('common:action.search', 'Search')}
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
              onSearch={onSearch}
              style={{ width: 220 }}
            />
            <Select
              allowClear
              placeholder={t('user.status')}
              style={{ width: 120 }}
              value={status}
              onChange={v => { setStatus(v); setPage(1); setTimeout(loadData, 0); }}
              options={[
                { value: 'active', label: t('user.enable') },
                { value: 'disabled', label: t('user.disable') },
              ]}
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                createForm.resetFields();
                setCreateOpen(true);
              }}
            >
              {t('user.create')}
            </Button>
          </Space>
        }
      >
        <Table<UserItem>
          rowKey="id"
          loading={loading}
          dataSource={data}
          columns={columns}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}
        />
      </Card>

      <Modal
        title={t('user.create')}
        open={createOpen}
        onOk={onCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnClose
        width={560}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="username" label={t('user.username')} rules={[{ required: true, min: 3, max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label={t('user.password')} rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="displayName" label={t('user.display_name')}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label={t('user.email')} rules={[{ type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label={t('user.phone')}>
            <Input />
          </Form.Item>
          <Form.Item name="preferredLocale" label={t('user.preferred_locale')}>
            <Select
              options={[
                { value: 'zh_CN', label: '中文' },
                { value: 'en_US', label: 'English' },
              ]}
              defaultValue="zh_CN"
              allowClear
            />
          </Form.Item>
          <Form.Item name="roleIds" label={t('user.roles')}>
            <Select
              mode="multiple"
              options={roles.map(r => ({ value: r.id, label: `${r.code} - ${localeLabel(r.name)}` }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={t('user.edit')}
        open={editOpen}
        onClose={() => setEditOpen(false)}
        width={520}
        extra={<Button type="primary" onClick={onEdit}>{t('common:action.save', 'Save')}</Button>}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="displayName" label={t('user.display_name')}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label={t('user.email')} rules={[{ type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label={t('user.phone')}>
            <Input />
          </Form.Item>
          <Form.Item name="preferredLocale" label={t('user.preferred_locale')}>
            <Select
              options={[
                { value: 'zh_CN', label: '中文' },
                { value: 'en_US', label: 'English' },
              ]}
              allowClear
            />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title={t('user.assign_roles')}
        open={roleOpen}
        onOk={onAssignRoles}
        onCancel={() => setRoleOpen(false)}
        destroyOnClose
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="roleIds" label={t('user.roles')}>
            <Select
              mode="multiple"
              options={roles.map(r => ({ value: r.id, label: `${r.code} - ${localeLabel(r.name)}` }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('user.reset_password')}
        open={pwdOpen}
        onOk={onResetPassword}
        onCancel={() => setPwdOpen(false)}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical">
          <Form.Item name="newPassword" label={t('user.new_password')} rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
