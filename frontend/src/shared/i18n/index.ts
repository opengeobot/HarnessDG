/**
 * 功能: i18next 国际化配置。中文为默认语言，英文为可切换语言。
 *       提供运行时语言切换与持久化（localStorage），并同步请求头 Accept-Language 取值。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import zh from './zh.json';
import en from './en.json';

/** 支持的语言（与后端 locale 取值 zh-CN / en-US 对应） */
export const SUPPORTED_LANGUAGES = ['zh', 'en'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

/** 语言持久化键：仅保存语言偏好，绝不写入令牌或用户数据 */
const LANGUAGE_STORAGE_KEY = 'aihub.language';

/** i18next 语言码 → 后端 Accept-Language 取值 */
const ACCEPT_LANGUAGE_BY_LANGUAGE: Record<SupportedLanguage, string> = {
  zh: 'zh-CN',
  en: 'en-US',
};

/** 判断给定值是否为受支持语言 */
export function isSupportedLanguage(value: unknown): value is SupportedLanguage {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

/** 读取持久化语言；无有效值时回退默认语言 zh */
export function resolveInitialLanguage(): SupportedLanguage {
  try {
    const stored = globalThis.localStorage?.getItem(LANGUAGE_STORAGE_KEY);
    if (isSupportedLanguage(stored)) {
      return stored;
    }
  } catch {
    // 隐私模式或存储被禁用时静默回退默认语言，不影响应用可用性。
  }
  return 'zh';
}

/** 返回当前语言对应的 Accept-Language 请求头取值 */
export function currentAcceptLanguage(): string {
  const language = i18n.language;
  return isSupportedLanguage(language)
    ? ACCEPT_LANGUAGE_BY_LANGUAGE[language]
    : ACCEPT_LANGUAGE_BY_LANGUAGE.zh;
}

/**
 * 运行时切换语言：更新 i18next、antd locale 与请求头取值，并持久化偏好。
 *
 * @param language 目标语言
 */
export async function changeLanguage(language: SupportedLanguage): Promise<void> {
  await i18n.changeLanguage(language);
  try {
    globalThis.localStorage?.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // 持久化失败不影响本次切换生效。
  }
}

void i18n.use(initReactI18next).init({
  resources: {
    zh: { translation: zh },
    en: { translation: en },
  },
  lng: resolveInitialLanguage(),
  fallbackLng: 'zh',
  interpolation: { escapeValue: false },
});

export default i18n;
