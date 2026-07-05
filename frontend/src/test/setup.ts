/**
 * Vitest 全局设置——导入 jest-dom 扩展 matchers（toBeInTheDocument 等），
 * 并为 jsdom 环境补充 antd 所需的 window API。
 */
import '@testing-library/jest-dom/vitest';

// antd 的 responsiveObserver 需要 window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

// antd 的 getComputedStyle 需要
const origGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = (elt: Element, pseudoElt?: string | null) => {
  try {
    return origGetComputedStyle(elt, pseudoElt);
  } catch {
    return {} as CSSStyleDeclaration;
  }
};
