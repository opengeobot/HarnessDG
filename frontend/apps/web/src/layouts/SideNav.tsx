/**
 * 功能：侧边导航栏 - 支持收起展开与系统管理子菜单
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Layout, Menu } from 'antd';
import {
  AppstoreOutlined,
  DeploymentUnitOutlined,
  SafetyCertificateOutlined,
  MessageOutlined,
  BugOutlined,
  SettingOutlined,
  TeamOutlined,
  KeyOutlined,
  BookOutlined,
  ControlOutlined,
  AuditOutlined,
  UserOutlined,
  // Phase 2 新增图标
  CheckCircleOutlined,
  FieldTimeOutlined,
  BranchesOutlined,
  CloudUploadOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/stores/authStore';

const { Sider } = Layout;

interface SideNavProps {
  collapsed: boolean;
}

function SideNav({ collapsed }: SideNavProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation('navigation');
  const { user } = useAuthStore();
  const isAdmin = user?.roles?.includes('admin');

  const settingsChildren: any[] = [
    {
      key: '/settings/profile',
      icon: <UserOutlined />,
      label: t('menu.settings_profile'),
    },
    {
      key: '/settings/dictionary',
      icon: <BookOutlined />,
      label: t('menu.settings_dictionary'),
    },
  ];

  if (isAdmin) {
    settingsChildren.unshift(
      {
        key: '/settings/users',
        icon: <TeamOutlined />,
        label: t('menu.settings_users'),
      },
      {
        key: '/settings/roles',
        icon: <KeyOutlined />,
        label: t('menu.settings_roles'),
      },
    );
    settingsChildren.push(
      {
        key: '/settings/config',
        icon: <ControlOutlined />,
        label: t('menu.settings_config'),
      },
      {
        key: '/settings/audit',
        icon: <AuditOutlined />,
        label: t('menu.settings_audit'),
      },
    );
  }

  const menuItems = [
    { key: '/tasks', icon: <AppstoreOutlined />, label: t('menu.task_center') },
    { key: '/ontology', icon: <DeploymentUnitOutlined />, label: t('menu.ontology') },
    { key: '/governance', icon: <SafetyCertificateOutlined />, label: t('menu.governance') },
    { key: '/query', icon: <MessageOutlined />, label: t('menu.query') },
    { key: '/diagnosis', icon: <BugOutlined />, label: t('menu.diagnosis') },
    // Phase 2 新增菜单
    { key: '/approval', icon: <CheckCircleOutlined />, label: t('menu.approval') },
    { key: '/quality', icon: <FieldTimeOutlined />, label: t('menu.quality') },
    { key: '/lineage', icon: <BranchesOutlined />, label: t('menu.lineage') },
    { key: '/data-ingestion', icon: <CloudUploadOutlined />, label: t('menu.data_ingestion') },
    { key: '/weekly-report', icon: <FileTextOutlined />, label: t('menu.weekly_report') },
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: t('menu.settings'),
      children: settingsChildren,
    },
  ];

  const selectedKey = location.pathname.startsWith('/settings/')
    ? location.pathname
    : location.pathname;

  return (
    <Sider
      collapsed={collapsed}
      width={240}
      collapsedWidth={64}
      style={{
        position: 'fixed',
        left: 0,
        top: 56,
        bottom: 0,
        zIndex: 900,
        background: 'var(--color-bg-card)',
        borderRight: '1px solid var(--color-border-default)',
        overflow: 'auto',
      }}
    >
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        defaultOpenKeys={collapsed ? [] : ['/settings']}
        items={menuItems}
        onClick={({ key }) => navigate(key)}
        style={{ border: 'none', marginTop: 8 }}
      />
    </Sider>
  );
}

export default SideNav;
