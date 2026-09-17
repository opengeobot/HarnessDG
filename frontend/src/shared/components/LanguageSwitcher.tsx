/**
 * 功能: 语言切换组件。在应用外壳与登录页提供 zh-CN/en-US 运行时切换入口，
 *       切换后同步 i18next、antd locale 与请求头 Accept-Language。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
import { useTranslation } from 'react-i18next';
import { Button, Dropdown } from 'antd';
import { GlobalOutlined } from '@ant-design/icons';
import { changeLanguage, type SupportedLanguage } from '@/shared/i18n';

/** 语言选项：显示名固定使用该语言自身的表述，避免依赖当前界面语言。 */
const LANGUAGE_OPTIONS: ReadonlyArray<{ key: SupportedLanguage; label: string }> = [
  { key: 'zh', label: '中文' },
  { key: 'en', label: 'English' },
];

export interface LanguageSwitcherProps {
  /** 按钮样式变体：应用外壳使用 text（深色顶栏），登录页使用 default */
  variant?: 'text' | 'default';
}

/**
 * 语言切换器。
 *
 * <p>切换动作经 {@code changeLanguage} 单一入口完成，保证 i18next、antd 与企业请求头三者同步。
 */
export function LanguageSwitcher({ variant = 'text' }: LanguageSwitcherProps) {
  const { t, i18n } = useTranslation();
  const current: SupportedLanguage = i18n.language === 'en' ? 'en' : 'zh';
  const currentLabel =
    LANGUAGE_OPTIONS.find((option) => option.key === current)?.label ?? '中文';

  return (
    <Dropdown
      trigger={['click']}
      menu={{
        selectedKeys: [current],
        items: LANGUAGE_OPTIONS.map((option) => ({
          key: option.key,
          label: option.label,
        })),
        onClick: ({ key }) => {
          if (key !== current) {
            void changeLanguage(key as SupportedLanguage);
          }
        },
      }}
    >
      <Button
        type={variant === 'text' ? 'text' : 'default'}
        size="small"
        data-testid="language-switcher"
        aria-label={t('layout.switchLanguage')}
        icon={<GlobalOutlined />}
        style={variant === 'text' ? { color: '#fff' } : undefined}
      >
        {currentLabel}
      </Button>
    </Dropdown>
  );
}
