/**
 * 功能：字典管理后台 - 分组管理和字典项管理（Tab切换）
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import {
  Card, Table, Button, Space, Tabs, Modal, Form, Input, Select, Switch, message, Popconfirm,
  Upload, Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, ImportOutlined, ExportOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { dictApi } from '@/services';
import { DictTag } from '@/components/dict';

interface DictGroup {
  id: number;
  code: string;
  name: Record<string, string>;
  description?: Record<string, string>;
  category: string;
  isTree?: boolean;
  status: string;
}

interface DictItem {
  id: number;
  groupCode: string;
  code: string;
  label: Record<string, string>;
  value: string;
  color?: string;
  sortOrder?: number;
  status: string;
}

export default function DictionaryAdmin() {
  const { t, i18n } = useTranslation(['dictionary', 'common']);
  const locale = i18n.language || 'zh_CN';

  const [groups, setGroups] = useState<DictGroup[]>([]);
  const [loadingGroups, setLoadingGroups] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null);
  const [items, setItems] = useState<DictItem[]>([]);
  const [loadingItems, setLoadingItems] = useState(false);

  const [groupModal, setGroupModal] = useState(false);
  const [editingGroup, setEditingGroup] = useState<DictGroup | null>(null);
  const [groupForm] = Form.useForm();

  const [itemModal, setItemModal] = useState(false);
  const [editingItem, setEditingItem] = useState<DictItem | null>(null);
  const [itemForm] = Form.useForm();

  const localeLabel = (m?: Record<string, string>) => m?.[locale] || m?.['zh_CN'] || m?.['en_US'] || '';

  // Load groups
  const loadGroups = async () => {
    setLoadingGroups(true);
    try {
      const res: any = await dictApi.listGroups();
      setGroups(res.data || []);
      if (!selectedGroup && res.data?.length > 0) {
        setSelectedGroup(res.data[0].code);
      }
    } finally {
      setLoadingGroups(false);
    }
  };

  // Load items for selected group
  const loadItems = async (code: string) => {
    setLoadingItems(true);
    try {
      const res: any = await dictApi.listItems(code);
      setItems(res.data || []);
    } finally {
      setLoadingItems(false);
    }
  };

  useEffect(() => { loadGroups(); }, []);
  useEffect(() => { if (selectedGroup) loadItems(selectedGroup); }, [selectedGroup]);

  // Group CRUD
  const handleSaveGroup = async () => {
    const v = await groupForm.validateFields();
    const payload = {
      code: v.code,
      name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
      description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
      category: v.category,
      isTree: !!v.isTree,
    };
    try {
      if (editingGroup) {
        await dictApi.updateGroup(editingGroup.code, payload);
      } else {
        await dictApi.createGroup(payload);
      }
      message.success(t('common:action.save_success', '保存成功'));
      setGroupModal(false);
      loadGroups();
    } catch {
      message.error(t('common:action.save_fail', '保存失败'));
    }
  };

  const handleDeleteGroup = async (code: string) => {
    try {
      await dictApi.deleteGroup(code);
      message.success(t('common:action.delete_success', '删除成功'));
      if (selectedGroup === code) setSelectedGroup(null);
      loadGroups();
    } catch {
      message.error(t('common:action.delete_fail', '删除失败'));
    }
  };

  const openCreateGroup = () => {
    setEditingGroup(null);
    groupForm.resetFields();
    setGroupModal(true);
  };

  const openEditGroup = (g: DictGroup) => {
    setEditingGroup(g);
    groupForm.setFieldsValue({
      code: g.code,
      nameZh: g.name?.zh_CN,
      nameEn: g.name?.en_US,
      descZh: g.description?.zh_CN,
      descEn: g.description?.en_US,
      category: g.category,
      isTree: g.isTree,
    });
    setGroupModal(true);
  };

  // Item CRUD
  const handleSaveItem = async () => {
    const v = await itemForm.validateFields();
    const label = { zh_CN: v.labelZh, en_US: v.labelEn || v.labelZh };
    try {
      if (editingItem) {
        await dictApi.updateItem(editingItem.id, {
          label,
          value: v.value,
          color: v.color,
          sortOrder: v.sortOrder,
          status: v.status,
        });
      } else {
        await dictApi.createItem({
          groupCode: selectedGroup,
          code: v.code,
          label,
          value: v.value,
          color: v.color,
          sortOrder: v.sortOrder,
        });
      }
      message.success(t('common:action.save_success', '保存成功'));
      setItemModal(false);
      if (selectedGroup) loadItems(selectedGroup);
    } catch {
      message.error(t('common:action.save_fail', '保存失败'));
    }
  };

  const handleDeleteItem = async (id: number) => {
    try {
      await dictApi.deleteItem(id);
      message.success(t('common:action.delete_success', '删除成功'));
      if (selectedGroup) loadItems(selectedGroup);
    } catch {
      message.error(t('common:action.delete_fail', '删除失败'));
    }
  };

  const openCreateItem = () => {
    setEditingItem(null);
    itemForm.resetFields();
    itemForm.setFieldsValue({ groupCode: selectedGroup });
    setItemModal(true);
  };

  const openEditItem = (item: DictItem) => {
    setEditingItem(item);
    itemForm.setFieldsValue({
      code: item.code,
      labelZh: item.label?.zh_CN,
      labelEn: item.label?.en_US,
      value: item.value,
      color: item.color,
      sortOrder: item.sortOrder,
      status: item.status,
    });
    setItemModal(true);
  };

  // Export / Import
  const handleExport = async () => {
    if (!selectedGroup) return;
    const res: any = await dictApi.exportGroup(selectedGroup);
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `dict-${selectedGroup}.json`;
    a.click();
    URL.revokeObjectURL(url);
    message.success(t('dictionary.import_export.export_success'));
  };

  const handleImport = async (payload: any, overwrite: boolean) => {
    try {
      await dictApi.importGroup(payload, overwrite);
      message.success(t('dictionary.import_export.import_success'));
      loadGroups();
    } catch {
      message.error(t('dictionary.import_export.import_fail'));
    }
  };

  const [importPayload, setImportPayload] = useState<any>(null);
  const [overwrite, setOverwrite] = useState(false);
  const [importModal, setImportModal] = useState(false);

  // Columns
  const groupCols: ColumnsType<DictGroup> = [
    { title: t('dictionary.group.code'), dataIndex: 'code', width: 180,
      render: (v: string) => <a onClick={() => setSelectedGroup(v)}>{v}</a> },
    { title: t('dictionary.group.name'), dataIndex: 'name',
      render: (v: Record<string, string>) => localeLabel(v) },
    { title: t('dictionary.group.category'), dataIndex: 'category', width: 120 },
    { title: t('dictionary.group.is_tree'), dataIndex: 'isTree', width: 80,
      render: (v: boolean) => v ? 'Y' : 'N' },
    {
      title: t('dictionary.group.actions'),
      fixed: 'right',
      width: 140,
      render: (_, r) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditGroup(r)} />
          <Popconfirm title={t('common:confirm.delete_desc')} onConfirm={() => handleDeleteGroup(r.code)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const itemCols: ColumnsType<DictItem> = [
    { title: t('dictionary.item.code'), dataIndex: 'code', width: 160 },
    { title: t('dictionary.item.label'), dataIndex: 'label',
      render: (v: Record<string, string>) => localeLabel(v) },
    { title: t('dictionary.item.value'), dataIndex: 'value', width: 120 },
    { title: t('dictionary.item.color'), dataIndex: 'color', width: 100,
      render: (v: string) => v ? <Tag color={v}>{v}</Tag> : '-' },
    { title: t('dictionary.item.sort_order'), dataIndex: 'sortOrder', width: 80 },
    { title: t('dictionary.item.status'), dataIndex: 'status', width: 80,
      render: (v: string) => <DictTag groupCode="dict_status" code={v} /> },
    {
      title: t('dictionary.group.actions'),
      fixed: 'right',
      width: 140,
      render: (_, r) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditItem(r)} />
          <Popconfirm title={t('common:confirm.delete_desc')} onConfirm={() => handleDeleteItem(r.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const tabItems = [
    {
      key: 'groups',
      label: t('dictionary.tabs.groups', '分组管理'),
      children: (
        <Card title={t('dictionary.tabs.groups')}
          extra={
            <Space>
              <Button icon={<ReloadOutlined />} onClick={loadGroups} />
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateGroup}>
                {t('dictionary.group.create')}
              </Button>
            </Space>
          }
        >
          <Table<DictGroup> rowKey="id" columns={groupCols} dataSource={groups}
            loading={loadingGroups} onRow={r => ({ onClick: () => setSelectedGroup(r.code) })} />
        </Card>
      ),
    },
    {
      key: 'items',
      label: t('dictionary.tabs.items', '字典项管理'),
      children: (
        <Card title={`${t('dictionary.tabs.items')} - ${selectedGroup || ''}`}
          extra={
            <Space>
              <Select allowClear placeholder={t('dictionary.group.code')} value={selectedGroup}
                onChange={setSelectedGroup} style={{ width: 180 }}
                options={groups.map(g => ({ value: g.code, label: `${g.code} (${localeLabel(g.name)})` }))}
              />
              <Button icon={<ReloadOutlined />} onClick={() => selectedGroup && loadItems(selectedGroup)} />
              <Button icon={<ExportOutlined />} onClick={handleExport} disabled={!selectedGroup}>
                {t('dictionary.import_export.export')}
              </Button>
              <Button icon={<ImportOutlined />} onClick={() => setImportModal(true)} disabled={!selectedGroup}>
                {t('dictionary.import_export.import')}
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateItem} disabled={!selectedGroup}>
                {t('dictionary.item.create')}
              </Button>
            </Space>
          }
        >
          <Table<DictItem> rowKey="id" columns={itemCols} dataSource={items} loading={loadingItems} />
        </Card>
      ),
    },
  ];

  return (
    <>
      <Tabs defaultActiveKey="groups" items={tabItems} />

      {/* Group Modal */}
      <Modal
        open={groupModal}
        title={editingGroup ? t('dictionary.group.edit') : t('dictionary.group.create')}
        onCancel={() => setGroupModal(false)}
        onOk={handleSaveGroup}
        width={560}
      >
        <Form form={groupForm} layout="vertical">
          <Form.Item name="code" label={t('dictionary.group.code')} rules={[{ required: true }]}>
            <Input disabled={!!editingGroup} />
          </Form.Item>
          <Form.Item name="nameZh" label={t('dictionary.group.name') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameEn" label={t('dictionary.group.name') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="descZh" label={t('dictionary.group.description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('dictionary.group.description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="category" label={t('dictionary.group.category')} rules={[{ required: true }]}>
            <Select options={[
              { value: 'system', label: t('dictionary.category.system') },
              { value: 'business', label: t('dictionary.category.business') },
              { value: 'model', label: t('dictionary.category.model') },
              { value: 'ui', label: t('dictionary.category.ui') },
            ]} />
          </Form.Item>
          <Form.Item name="isTree" label={t('dictionary.group.is_tree')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* Item Modal */}
      <Modal
        open={itemModal}
        title={editingItem ? t('dictionary.item.edit') : t('dictionary.item.create')}
        onCancel={() => setItemModal(false)}
        onOk={handleSaveItem}
        width={560}
      >
        <Form form={itemForm} layout="vertical">
          <Form.Item name="code" label={t('dictionary.item.code')} rules={[{ required: true }]}>
            <Input disabled={!!editingItem} />
          </Form.Item>
          <Form.Item name="labelZh" label={t('dictionary.item.label') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="labelEn" label={t('dictionary.item.label') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="value" label={t('dictionary.item.value')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="color" label={t('dictionary.item.color')}>
            <Input placeholder="green / #52c41a" />
          </Form.Item>
          <Form.Item name="sortOrder" label={t('dictionary.item.sort_order')}>
            <Input type="number" />
          </Form.Item>
          {editingItem && (
            <Form.Item name="status" label={t('dictionary.item.status')}>
              <Select options={[
                { value: 'active', label: 'active' },
                { value: 'disabled', label: 'disabled' },
              ]} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* Import Modal */}
      <Modal
        open={importModal}
        title={t('dictionary.import_export.import_title', '导入字典')}
        onCancel={() => setImportModal(false)}
        onOk={() => { importPayload && handleImport(importPayload, overwrite); setImportModal(false); }}
      >
        <Upload.Dragger
          accept=".json"
          beforeUpload={(file) => {
            const reader = new FileReader();
            reader.onload = (ev) => {
              try {
                setImportPayload(JSON.parse(ev.target?.result as string));
                message.success(file.name);
              } catch {
                message.error('Invalid JSON');
              }
            };
            reader.readAsText(file);
            return false;
          }}
        >
          <p>{t('dictionary.import_export.import_hint', '点击或拖拽 JSON 文件到此处')}</p>
        </Upload.Dragger>
        <div style={{ marginTop: 12 }}>
          <label>
            <input type="checkbox" checked={overwrite}
              onChange={e => setOverwrite(e.target.checked)} />
            {' '}{t('dictionary.import_export.overwrite', '覆盖已有数据')}
          </label>
        </div>
      </Modal>
    </>
  );
}
