/**
 * 功能：全局布局组件 - 顶部导航 + 侧边栏 + 内容区
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Layout } from 'antd';
import { Outlet } from 'react-router-dom';
import { useState } from 'react';
import TopNav from './TopNav';
import SideNav from './SideNav';
import { useTranslation } from 'react-i18next';

const { Content } = Layout;

function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const { t } = useTranslation('navigation');

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <TopNav collapsed={collapsed} onMenuToggle={() => setCollapsed(!collapsed)} />
      <Layout>
        <SideNav collapsed={collapsed} />
        <Content
          style={{
            marginLeft: collapsed ? 64 : 240,
            marginTop: 56,
            padding: 24,
            transition: 'margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
            background: 'var(--color-bg-page)',
            minHeight: 'calc(100vh - 56px)',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

export default AppLayout;
