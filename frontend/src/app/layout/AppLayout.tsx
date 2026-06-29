/**
 * 功能: 应用外壳布局。Ant Design Layout，顶部品牌栏 + 侧边导航占位 + 内容区出口。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { Layout, Menu, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { NAV_ITEMS } from './navigation';

const { Header, Sider, Content } = Layout;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey =
    NAV_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ??
    NAV_ITEMS[0].key;

  const menuItems = NAV_ITEMS.map((item) => ({
    key: item.key,
    label: item.label,
  }));

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center' }}>
        <Typography.Title
          level={4}
          style={{ color: '#fff', margin: 0, whiteSpace: 'nowrap' }}
        >
          AI 资产管理平台
        </Typography.Title>
      </Header>
      <Layout>
        <Sider width={220} breakpoint="lg" collapsedWidth="0" theme="light">
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
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
