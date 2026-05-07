/**
 * 功能：数据字典管理页面（分组 + 字典项）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Card, Table, Button, Space, Tag, Modal, Form, Input, Select, Switch,
  message, Popconfirm, Drawer, Upload, InputNumber,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined, ImportOutlined, ExportOutlined, ReloadOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { dictApi } from '@/services';
import { useDictStore } from '@/stores/dictStore';

interface DictGroup {
  id: number;
  code: string;
  name: Record<string, string>;
  description?: Record<string, string>;
  category: string;
  isTree?: boolean;
  isMultiple?: boolean;
  isEditable?: boolean;
  status: string;
}

interface DictItem {
  id: number;
  groupCode: string;
  parentId?: number;
  code: string;
  label: Record<string, string>;
  description?: Record<string, string>;
  value: string;
  icon?: string;
  color?: string;
  sortOrder?: number;
  isDefault?: boolean;
  isSystem?: boolean;
  status: string;
}

export default function DictionaryPage() {
  const { t, i18n } = useTranslation(['dictionary', 'common']);
  const locale = i18n.language || 'zh_CN';
  const invalidateAll = useDictStore(s => s.invalidateAll);

  const [groups, setGroups] = useState<DictGroup[]>([]);
  const [category, setCategory] = useState<string | undefined>();
  const [loadingGroups, setLoadingGroups] = useState(false);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  const [items, setItems] = useState<DictItem[]>([]);
  const [loadingItems, setLoadingItems] = useState(false);

  const [groupOpen, setGroupOpen] = useState(false);
  const [groupEditing, setGroupEditing] = useState<DictGroup | null>(null);
  const [groupForm] = Form.useForm();

  const [itemOpen, setItemOpen] = useState(false);
  const [itemEditing, setItemEditing] = useState<DictItem | null>(null);
  const [itemForm] = Form.useForm();

  const [importOpen, setImportOpen] = useState(false);
  const [importPayload, setImportPayload] = useState<any>(null);
  const [overwrite, setOverwrite] = useState(false);

  const localeLabel = (m?: Record<string, string>) =>
    m?.[locale] || m?.['zh_CN'] || m?.['en_US'] || '';

  const loadGroups = async () => {
    setLoadingGroups(true);
    try {
      const res: any = await dictApi.listGroups({ category });
      const list: DictGroup[] = res.data || [];
      setGroups(list);
      if (!selectedCode && list.length > 0) {
        setSelectedCode(list[0].code);
      } else if (selectedCode && !list.find(g => g.code === selectedCode) && list.length > 0) {
        setSelectedCode(list[0].code);
      }
    } finally {
      setLoadingGroups(false);
    }
  };

  const loadItems = async (code: string) => {
    setLoadingItems(true);
    try {
      const res: any = await dictApi.listItems(code);
      setItems(res.data || []);
    } finally {
      setLoadingItems(false);
    }
  };

  useEffect(() => { loadGroups(); /* eslint-disable-next-line */ }, [category]);
  useEffect(() => { if (selectedCode) loadItems(selectedCode); }, [selectedCode]);

  const currentGroup = useMemo(
    () => groups.find(g => g.code === selectedCode) || null,
    [groups, selectedCode],
  );

  const saveGroup = async () => {
    const v = await groupForm.validateFields();
    const payload = {
      code: v.code,
      name: { zh_CN: v.nameZh, en_US: v.nameEn || v.nameZh },
      description: { zh_CN: v.descZh || '', en_US: v.descEn || '' },
      category: v.category || 'business',
      isTree: !!v.isTree,
      isMultiple: !!v.isMultiple,
      isEditable: v.isEditable !== false,
    };
    if (groupEditing) {
      await dictApi.updateGroup(groupEditing.code, payload);
    } else {
      await dictApi.createGroup(payload);
    }
    message.success(t('common:action.save_success', 'Saved'));
    setGroupOpen(false);
    invalidateAll();
    loadGroups();
  };

  const deleteGroup = async (code: string) => {
    await dictApi.deleteGroup(code);
    message.success(t('common:action.delete_success', 'Deleted'));
    if (selectedCode === code) setSelectedCode(null);
    invalidateAll();
    loadGroups();
  };

  const openCreateGroup = () => {
    setGroupEditing(null);
    groupForm.resetFields();
    groupForm.setFieldsValue({ category: 'business', isEditable: true });
    setGroupOpen(true);
  };

  const openEditGroup = (g: DictGroup) => {
    setGroupEditing(g);
    groupForm.setFieldsValue({
      code: g.code,
      nameZh: g.name?.zh_CN,
      nameEn: g.name?.en_US,
      descZh: g.description?.zh_CN,
      descEn: g.description?.en_US,
      category: g.category,
      isTree: g.isTree,
      isMultiple: g.isMultiple,
      isEditable: g.isEditable,
    });
    setGroupOpen(true);
  };

  const openCreateItem = () => {
    if (!currentGroup) return;
    setItemEditing(null);
    itemForm.resetFields();
    itemForm.setFieldsValue({ groupCode: currentGroup.code, sortOrder: 0 });
    setItemOpen(true);
  };

  const openEditItem = (item: DictItem) => {
    setItemEditing(item);
    itemForm.setFieldsValue({
      groupCode: item.groupCode,
      parentId: item.parentId,
      code: item.code,
      labelZh: item.label?.zh_CN,
      labelEn: item.label?.en_US,
      value: item.value,
      icon: item.icon,
      color: item.color,
      sortOrder: item.sortOrder,
      isDefault: item.isDefault,
      status: item.status,
    });
    setItemOpen(true);
  };

  const saveItem = async () => {
    const v = await itemForm.validateFields();
    const label = { zh_CN: v.labelZh, en_US: v.labelEn || v.labelZh };
    if (itemEditing) {
      await dictApi.updateItem(itemEditing.id, {
        parentId: v.parentId,
        label,
        value: v.value,
        icon: v.icon,
        color: v.color,
        sortOrder: v.sortOrder,
        isDefault: v.isDefault,
        status: v.status,
      });
    } else {
      await dictApi.createItem({
        groupCode: v.groupCode,
        parentId: v.parentId,
        code: v.code,
        label,
        value: v.value,
        icon: v.icon,
        color: v.color,
        sortOrder: v.sortOrder,
        isDefault: v.isDefault,
      });
    }
    message.success(t('common:action.save_success', 'Saved'));
    setItemOpen(false);
    invalidateAll();
    if (selectedCode) loadItems(selectedCode);
  };

  const deleteItem = async (id: number) => {
    await dictApi.deleteItem(id);
    message.success(t('common:action.delete_success', 'Deleted'));
    invalidateAll();
    if (selectedCode) loadItems(selectedCode);
  };

  const onExport = async () => {
    if (!currentGroup) return;
    const res: any = await dictApi.exportGroup(currentGroup.code);
    const data = res.data;
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `dict-${currentGroup.code}.json`;
    a.click();
    URL.revokeObjectURL(url);
    message.success(t('import_export.export_success'));
  };

  const onImport = async () => {
    if (!importPayload) return;
    await dictApi.importGroup(importPayload, overwrite);
    message.success(t('import_export.import_success'));
    setImportOpen(false);
    setImportPayload(null);
    invalidateAll();
    loadGroups();
  };

  const groupCols: ColumnsType<DictGroup> = [
    {
      title: t('group.code'),
      dataIndex: 'code',
      render: (code: string, r) => (
        <a onClick={() => setSelectedCode(code)}
           style={{ fontWeight: r.code === selectedCode ? 700 : 400 }}>
          {code}
        </a>
      ),
    },
    {
      title: t('group.name'),
      dataIndex: 'name',
      render: (name) => localeLabel(name),
    },
    { title: t('group.category'), dataIndex: 'category', width: 100 },
  ];

  const itemCols: ColumnsType<DictItem> = [
    { title: t('item.code'), dataIndex: 'code', width: 160 },
    {
      title: t('item.label'),
      dataIndex: 'label',
      render: (label) => localeLabel(label),
    },
    { title: t('item.value'), dataIndex: 'value', width: 140 },
    { title: t('item.sort_order'), dataIndex: 'sortOrder', width: 80 },
    {
      title: t('item.color'),
      dataIndex: 'color',
      width: 100,
      render: (c: string) => c ? <Tag color={c}>{c}</Tag> : '-',
    },
    {
      title: t('item.is_default'),
      dataIndex: 'isDefault',
      width: 80,
      render: (b: boolean) => b ? '✓' : '',
    },
    {
      title: t('item.is_system'),
      dataIndex: 'isSystem',
      width: 90,
      render: (b: boolean) => b ? <Tag color="gold">S</Tag> : '',
    },
    { title: t('item.status'), dataIndex: 'status', width: 80 },
    {
      title: t('group.actions'),
      fixed: 'right',
      width: 160,
      render: (_, r) => (
        <Space size={4}>
          <Button size="small" onClick={() => openEditItem(r)}>{t('common:action.edit', 'Edit')}</Button>
          {!r.isSystem && (
            <Popconfirm title={t('common:confirm.delete_desc')} onConfirm={() => deleteItem(r.id)}>
              <Button size="small" danger>{t('common:action.delete', 'Delete')}</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '360px 1fr', gap: 16 }}>
      <Card
        title={t('tabs.groups')}
        size="small"
        extra={
          <Space size={4}>
            <Select
              allowClear
              size="small"
              placeholder={t('group.category')}
              value={category}
              onChange={setCategory}
              style={{ width: 110 }}
              options={[
                { value: 'system', label: t('category.system') },
                { value: 'business', label: t('category.business') },
                { value: 'model', label: t('category.model') },
                { value: 'ui', label: t('category.ui') },
              ]}
            />
            <Button size="small" icon={<ReloadOutlined />} onClick={loadGroups} />
            <Button size="small" type="primary" icon={<PlusOutlined />} onClick={openCreateGroup} />
          </Space>
        }
      >
        <Table<DictGroup>
          rowKey="id"
          size="small"
          loading={loadingGroups}
          columns={groupCols}
          dataSource={groups}
          pagination={false}
          onRow={(r) => ({ onClick: () => setSelectedCode(r.code) })}
          rowClassName={r => r.code === selectedCode ? 'ant-table-row-selected' : ''}
        />
      </Card>

      <Card
        title={currentGroup ? `${t('tabs.items')} - ${localeLabel(currentGroup.name)}` : t('tabs.items')}
        extra={
          <Space>
            {currentGroup && (
              <>
                <Button icon={<ExportOutlined />} onClick={onExport}>
                  {t('import_export.export')}
                </Button>
                <Button icon={<ImportOutlined />} onClick={() => setImportOpen(true)}>
                  {t('import_export.import')}
                </Button>
                <Button onClick={() => openEditGroup(currentGroup)}>
                  {t('group.edit')}
                </Button>
                <Popconfirm
                  title={t('group.delete_confirm')}
                  onConfirm={() => deleteGroup(currentGroup.code)}
                >
                  <Button danger>{t('common:action.delete', 'Delete')}</Button>
                </Popconfirm>
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreateItem}>
                  {t('item.create')}
                </Button>
              </>
            )}
          </Space>
        }
      >
        <Table<DictItem>
          rowKey="id"
          size="small"
          loading={loadingItems}
          columns={itemCols}
          dataSource={items}
          scroll={{ x: 1100 }}
          pagination={{ pageSize: 50 }}
        />
      </Card>

      <Drawer
        open={groupOpen}
        title={groupEditing ? t('group.edit') : t('group.create')}
        onClose={() => setGroupOpen(false)}
        width={520}
        extra={<Button type="primary" onClick={saveGroup}>{t('common:action.save', 'Save')}</Button>}
      >
        <Form form={groupForm} layout="vertical">
          <Form.Item name="code" label={t('group.code')} rules={[{ required: true }]}>
            <Input disabled={!!groupEditing} />
          </Form.Item>
          <Form.Item name="nameZh" label={t('group.name') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nameEn" label={t('group.name') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="descZh" label={t('group.description') + ' (中文)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="descEn" label={t('group.description') + ' (English)'}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="category" label={t('group.category')}>
            <Select options={[
              { value: 'system', label: t('category.system') },
              { value: 'business', label: t('category.business') },
              { value: 'model', label: t('category.model') },
              { value: 'ui', label: t('category.ui') },
            ]} />
          </Form.Item>
          <Form.Item name="isTree" label={t('group.is_tree')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="isMultiple" label={t('group.is_multiple')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="isEditable" label={t('group.is_editable')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        open={itemOpen}
        title={itemEditing ? t('item.edit') : t('item.create')}
        onClose={() => setItemOpen(false)}
        width={520}
        extra={<Button type="primary" onClick={saveItem}>{t('common:action.save', 'Save')}</Button>}
      >
        <Form form={itemForm} layout="vertical">
          <Form.Item name="groupCode" label={t('group.code')}>
            <Input disabled />
          </Form.Item>
          <Form.Item name="code" label={t('item.code')} rules={[{ required: true }]}>
            <Input disabled={!!itemEditing} />
          </Form.Item>
          <Form.Item name="labelZh" label={t('item.label') + ' (中文)'} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="labelEn" label={t('item.label') + ' (English)'}>
            <Input />
          </Form.Item>
          <Form.Item name="value" label={t('item.value')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          {currentGroup?.isTree && (
            <Form.Item name="parentId" label={t('item.parent')}>
              <Select allowClear options={items.map(it => ({
                value: it.id,
                label: `${it.code} - ${localeLabel(it.label)}`,
              }))} />
            </Form.Item>
          )}
          <Form.Item name="icon" label={t('item.icon')}>
            <Input />
          </Form.Item>
          <Form.Item name="color" label={t('item.color')}>
            <Input placeholder="green / #52c41a" />
          </Form.Item>
          <Form.Item name="sortOrder" label={t('item.sort_order')}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="isDefault" label={t('item.is_default')} valuePropName="checked">
            <Switch />
          </Form.Item>
          {itemEditing && (
            <Form.Item name="status" label={t('item.status')}>
              <Select options={[
                { value: 'active', label: 'active' },
                { value: 'disabled', label: 'disabled' },
              ]} />
            </Form.Item>
          )}
        </Form>
      </Drawer>

      <Modal
        open={importOpen}
        title={t('import_export.import_title')}
        onCancel={() => setImportOpen(false)}
        onOk={onImport}
        okButtonProps={{ disabled: !importPayload }}
      >
        <p style={{ color: '#888' }}>{t('import_export.import_hint')}</p>
        <Upload.Dragger
          accept=".json"
          multiple={false}
          beforeUpload={(file) => {
            const reader = new FileReader();
            reader.onload = (ev) => {
              try {
                const json = JSON.parse(ev.target?.result as string);
                setImportPayload(json);
                message.success(file.name);
              } catch (e) {
                message.error('Invalid JSON');
              }
            };
            reader.readAsText(file);
            return false;
          }}
        >
          <p>Click or drag JSON file here</p>
        </Upload.Dragger>
        <div style={{ marginTop: 12 }}>
          <label>
            <input
              type="checkbox"
              checked={overwrite}
              onChange={e => setOverwrite(e.target.checked)}
            />
            {' '}{t('import_export.overwrite')}
          </label>
        </div>
      </Modal>
    </div>
  );
}
