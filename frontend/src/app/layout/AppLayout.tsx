/**
 * 功能: 应用外壳布局。顶部品牌栏 + 用户信息/登出 + 侧边导航（按权限过滤，支持子菜单）+ 内容出口。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo } from 'react';
import { App, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { DownOutlined, UserOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/app/auth';
import { usePermission } from '@/app/permission';
import { isApiError } from '@/shared/api';
import { NAV_ITEMS, type NavItem } from './navigation';

const { Header, Sider, Content } = Layout;

type MenuNode = Required<MenuProps>['items'][number];

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const { principal, logout } = useAuth();
  const { hasAllScopes } = usePermission();

  // 按权限过滤导航项（父项若无可见子项则隐藏）
  const menuItems = useMemo<MenuNode[]>(() => {
    const visible = (item: NavItem): boolean =>
      !item.requiredScopes || hasAllScopes(item.requiredScopes);

    const build = (items: readonly NavItem[]): MenuNode[] => {
      const nodes: MenuNode[] = [];
      for (const item of items) {
        if (item.children) {
          const children = build(item.children);
          if (children.length > 0) {
            nodes.push({ key: item.key, label: item.label, children });
          }
          continue;
        }
        if (visible(item)) {
          nodes.push({ key: item.key, label: item.label });
        }
      }
      return nodes;
    };

    return build(NAV_ITEMS);
  }, [hasAllScopes]);

  const selectedKey = location.pathname;
  const openKeys = NAV_ITEMS.filter(
    (item) => item.children && location.pathname.startsWith(item.key),
  ).map((item) => item.key);

  const handleLogout = async () => {
    try {
      await logout();
    } catch (error) {
      message.error(isApiError(error) ? error.message : '登出失败');
    } finally {
      navigate('/login', { replace: true });
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0, whiteSpace: 'nowrap' }}>
          AI 资产管理平台
        </Typography.Title>
        <Dropdown
          menu={{
            items: [
              { key: 'profile', label: '个人中心' },
              { key: 'logout', label: '登出' },
            ],
            onClick: ({ key }) => {
              if (key === 'profile') {
                navigate('/profile');
              } else if (key === 'logout') {
                void handleLogout();
              }
            },
          }}
        >
          <Space style={{ color: '#fff', cursor: 'pointer' }}>
            <UserOutlined />
            {principal?.displayName ?? principal?.subject ?? '未登录'}
            <DownOutlined />
          </Space>
        </Dropdown>
      </Header>
      <Layout>
        <Sider width={220} breakpoint="lg" collapsedWidth="0" theme="light">
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            defaultOpenKeys={openKeys}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ height: '100%', borderRight: 0 }}
          />
        </Sider>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
