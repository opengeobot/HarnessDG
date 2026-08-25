// i18n 基础设施（管理后台计划 §七）：react-i18next + zh/en 资源。
// 扩展语种：新增 locales/<code>.json + 在 LANGUAGES 追加一项即可。
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import zh from './locales/zh.json';
import en from './locales/en.json';

/** 支持的语言列表（切换器数据源）。 */
export const LANGUAGES = [
  { code: 'zh', label: '中文' },
  { code: 'en', label: 'English' },
] as const;

export type LangCode = (typeof LANGUAGES)[number]['code'];

export const LANG_STORAGE_KEY = 'lang';

/** 初始语言：localStorage('lang') 优先，其次按 navigator 判定（非中文一律 en）。 */
export function detectLanguage(): LangCode {
  const saved = localStorage.getItem(LANG_STORAGE_KEY);
  if (saved && LANGUAGES.some((l) => l.code === saved)) {
    return saved as LangCode;
  }
  return (navigator.language || 'zh').toLowerCase().startsWith('zh') ? 'zh' : 'en';
}

/** 切换语言并持久化。 */
export function changeLanguage(code: LangCode): void {
  localStorage.setItem(LANG_STORAGE_KEY, code);
  i18n.changeLanguage(code);
}

i18n.use(initReactI18next).init({
  resources: {
    zh: { translation: zh },
    en: { translation: en },
  },
  lng: detectLanguage(),
  fallbackLng: 'zh',
  interpolation: { escapeValue: false },
});

export default i18n;
