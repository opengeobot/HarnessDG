/**
 * SafeMarkdown 组件测试——Markdown 渲染与 XSS 过滤。
 */
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { SafeMarkdown } from './SafeMarkdown';

describe('SafeMarkdown', () => {
  it('正常渲染 Markdown 标题', () => {
    const { container } = render(<SafeMarkdown content="# Hello World" />);
    const h1 = container.querySelector('h1');
    expect(h1).not.toBeNull();
    expect(h1?.textContent).toBe('Hello World');
  });

  it('正常渲染 Markdown 段落和强调', () => {
    const { container } = render(<SafeMarkdown content="This is **bold** and *italic*" />);
    const strong = container.querySelector('strong');
    const em = container.querySelector('em');
    expect(strong?.textContent).toBe('bold');
    expect(em?.textContent).toBe('italic');
  });

  it('渲染 Markdown 列表', () => {
    const { container } = render(<SafeMarkdown content={'- item1\n- item2\n- item3\n'} />);
    const items = container.querySelectorAll('li');
    expect(items.length).toBeGreaterThanOrEqual(3);
  });

  it('过滤 XSS script 标签', () => {
    const { container } = render(
      <SafeMarkdown content={'<script>alert("xss")</script>\n\nSafe text'} />,
    );
    const script = container.querySelector('script');
    expect(script).toBeNull();
    expect(container.textContent).toContain('Safe text');
    expect(container.textContent).not.toContain('alert');
  });

  it('过滤 iframe 和 object 标签', () => {
    const xssContent = '<iframe src="evil.com"></iframe><object data="evil"></object>';
    const { container } = render(<SafeMarkdown content={xssContent} />);
    expect(container.querySelector('iframe')).toBeNull();
    expect(container.querySelector('object')).toBeNull();
  });

  it('保留合法的链接标签', () => {
    const { container } = render(
      <SafeMarkdown content="[Click here](https://example.com)" />,
    );
    const link = container.querySelector('a');
    expect(link).not.toBeNull();
    expect(link?.getAttribute('href')).toBe('https://example.com');
  });

  it('应用自定义 className', () => {
    const { container } = render(
      <SafeMarkdown content="test" className="my-markdown" />,
    );
    const div = container.firstElementChild;
    expect(div).toHaveClass('my-markdown');
  });
});
