/**
 * 功能: 语言切换组件测试——验证切换入口存在、切换后文案与持久化生效。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import i18n, { changeLanguage } from '@/shared/i18n';
import { LanguageSwitcher } from './LanguageSwitcher';

describe('LanguageSwitcher', () => {
  afterEach(async () => {
    await changeLanguage('zh');
    globalThis.localStorage?.clear();
  });

  it('渲染当前语言并提供切换入口', () => {
    render(
      <AntdApp>
        <LanguageSwitcher />
      </AntdApp>,
    );

    expect(screen.getByTestId('language-switcher')).toBeInTheDocument();
    expect(screen.getByText('中文')).toBeInTheDocument();
  });

  it('选择 English 后切换语言并持久化偏好', async () => {
    const user = userEvent.setup();
    render(
      <AntdApp>
        <LanguageSwitcher />
      </AntdApp>,
    );

    await user.click(screen.getByTestId('language-switcher'));
    await user.click(await screen.findByText('English'));

    await waitFor(() => expect(i18n.language).toBe('en'));
    expect(globalThis.localStorage?.getItem('aihub.language')).toBe('en');
  });

  it('切换语言后立即反映为对应语言标签', async () => {
    await changeLanguage('en');
    render(
      <AntdApp>
        <LanguageSwitcher />
      </AntdApp>,
    );

    expect(await screen.findByText('English')).toBeInTheDocument();
  });
});
