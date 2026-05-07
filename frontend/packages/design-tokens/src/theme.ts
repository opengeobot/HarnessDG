/**
 * 功能：Ant Design 5.x 亮色主题配置，映射品牌 Token 到 AntD Token
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import type { ThemeConfig } from 'antd';

export const lightTheme: ThemeConfig = {
  token: {
    colorPrimary: '#1A365D',
    colorSuccess: '#339966',
    colorWarning: '#E8920C',
    colorError: '#CC3333',
    colorInfo: '#3373B3',
    colorBgContainer: '#FFFFFF',
    colorBgLayout: '#F8FAFC',
    colorBorder: '#E2E8F0',
    colorBorderSecondary: '#EDF2F7',
    colorText: '#1A2940',
    colorTextSecondary: '#5F7080',
    colorTextTertiary: '#8FA3B3',
    borderRadius: 8,
    fontFamily: '"Inter", "Noto Sans SC", -apple-system, BlinkMacSystemFont, sans-serif',
    fontSize: 14,
    controlHeight: 36,
  },
  components: {
    Layout: {
      headerBg: '#FFFFFF',
      headerHeight: 56,
      siderBg: '#FFFFFF',
    },
    Menu: {
      itemBorderRadius: 8,
      itemMarginInline: 8,
    },
    Card: {
      borderRadiusLG: 12,
    },
    Button: {
      borderRadius: 8,
      controlHeight: 36,
    },
    Input: {
      borderRadius: 8,
    },
  },
};

export const darkTheme: ThemeConfig = {
  token: {
    colorPrimary: '#4A8FCC',
    colorBgContainer: '#1A2332',
    colorBgLayout: '#0F1722',
    colorBorder: '#2D3E52',
    colorText: '#E8ECF0',
    colorTextSecondary: '#8FA3B3',
    borderRadius: 8,
    fontFamily: '"Inter", "Noto Sans SC", -apple-system, BlinkMacSystemFont, sans-serif',
  },
  components: {
    Layout: {
      headerBg: '#1A2332',
      siderBg: '#1A2332',
    },
  },
};
