/**
 * 功能：顶部导航栏 - Logo、搜索、通知、语言切换、用户
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Layout, Input, Space, Button, Dropdown, Avatar } from 'antd';
import {
  SearchOutlined,
  BellOutlined,
  GlobalOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

const { Header } = Layout;

interface TopNavProps {
  collapsed: boolean;
  onMenuToggle: () => void;
}

function TopNav({ collapsed, onMenuToggle }: TopNavProps) {
  const { t, i18n } = useTranslation('common');

  const handleLanguageSwitch = () => {
    const nextLang = i18n.language === 'zh_CN' ? 'en_US' : 'zh_CN';
    i18n.changeLanguage(nextLang);
    localStorage.setItem('harnessdg_locale', nextLang);
  };

  return (
    <Header
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 1000,
        height: 56,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px',
        background: 'var(--color-bg-card)',
        borderBottom: '1px solid var(--color-border-default)',
        boxShadow: 'var(--shadow-xs)',
      }}
    >
      <Space align="center" size={16}>
        <Button
          type="text"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={onMenuToggle}
          style={{ fontSize: 16 }}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <img src="/logo.png" alt="HarnessDG" style={{ height: 32, width: 32, objectFit: 'contain' }} />
          <span style={{ fontWeight: 700, fontSize: 18, color: 'var(--color-brand-primary)' }}>
            HarnessDG
          </span>
        </div>
      </Space>

      <Input
        prefix={<SearchOutlined />}
        placeholder={t('action.search') + ' (Cmd+K)'}
        style={{ maxWidth: 400, borderRadius: 20 }}
        allowClear
      />

      <Space size={12}>
        <Button type="text" icon={<BellOutlined />} />
        <Button
          type="text"
          icon={<GlobalOutlined />}
          onClick={handleLanguageSwitch}
        >
          {i18n.language === 'zh_CN' ? '中' : 'EN'}
        </Button>
        <Avatar icon={<UserOutlined />} style={{ backgroundColor: 'var(--color-brand-primary)' }} />
      </Space>
    </Header>
  );
}

export default TopNav;
