/**
 * 功能: 安全 Markdown 渲染组件——使用 marked 解析 + DOMPurify 过滤 XSS。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
import { useMemo } from 'react';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

export interface SafeMarkdownProps {
  /** 原始 Markdown 文本 */
  content: string;
  /** 外层容器 className */
  className?: string;
  /** 外层容器 style */
  style?: React.CSSProperties;
}

// 配置 marked：禁用 GFM 换行以减少意外渲染
marked.setOptions({ gfm: true, breaks: false });

/**
 * 将 Markdown 文本解析为 HTML 后经 DOMPurify 过滤，
 * 以 dangerouslySetInnerHTML 输出到 <div>。
 */
export function SafeMarkdown({ content, className, style }: SafeMarkdownProps) {
  const sanitizedHtml = useMemo(() => {
    const rawHtml = marked.parse(content, { async: false }) as string;
    return DOMPurify.sanitize(rawHtml, {
      // 允许常见 Markdown 元素，禁止 script/iframe/object/embed
      ALLOWED_TAGS: [
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
        'p', 'br', 'hr',
        'ul', 'ol', 'li',
        'blockquote', 'pre', 'code',
        'a', 'strong', 'em', 'del', 'sub', 'sup',
        'table', 'thead', 'tbody', 'tr', 'th', 'td',
        'img', 'span', 'div',
      ],
      ALLOWED_ATTR: [
        'href', 'target', 'rel', 'src', 'alt', 'title',
        'class', 'id',
        'colspan', 'rowspan', 'align',
      ],
      // 强制所有链接新窗口打开
      ADD_ATTR: ['target'],
    });
  }, [content]);

  return (
    <div
      className={className}
      style={{
        whiteSpace: 'pre-wrap',
        background: '#f5f5f5',
        padding: 12,
        borderRadius: 4,
        overflowX: 'auto',
        ...style,
      }}
      dangerouslySetInnerHTML={{ __html: sanitizedHtml }}
    />
  );
}
