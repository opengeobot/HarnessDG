/**
 * 功能：i18next 初始化配置
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import zhCommon from '../locales/zh_CN/common.json';
import zhNavigation from '../locales/zh_CN/navigation.json';
import zhTask from '../locales/zh_CN/task.json';
import zhValidation from '../locales/zh_CN/validation.json';
import zhDictionary from '../locales/zh_CN/dictionary.json';
import zhSettings from '../locales/zh_CN/settings.json';
import zhConfig from '../locales/zh_CN/config.json';
import zhAudit from '../locales/zh_CN/audit.json';

import enCommon from '../locales/en_US/common.json';
import enNavigation from '../locales/en_US/navigation.json';
import enTask from '../locales/en_US/task.json';
import enValidation from '../locales/en_US/validation.json';
import enDictionary from '../locales/en_US/dictionary.json';
import enSettings from '../locales/en_US/settings.json';
import enConfig from '../locales/en_US/config.json';
import enAudit from '../locales/en_US/audit.json';

export function initI18n() {
  i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      resources: {
        zh_CN: {
          common: zhCommon,
          navigation: zhNavigation,
          task: zhTask,
          validation: zhValidation,
          dictionary: zhDictionary,
          settings: zhSettings,
          config: zhConfig,
          audit: zhAudit,
        },
        en_US: {
          common: enCommon,
          navigation: enNavigation,
          task: enTask,
          validation: enValidation,
          dictionary: enDictionary,
          settings: enSettings,
          config: enConfig,
          audit: enAudit,
        },
      },
      fallbackLng: 'zh_CN',
      defaultNS: 'common',
      ns: ['common', 'navigation', 'task', 'validation', 'dictionary', 'settings', 'config', 'audit'],
      interpolation: {
        escapeValue: false,
      },
      detection: {
        order: ['localStorage', 'navigator'],
        lookupLocalStorage: 'harnessdg_locale',
        caches: ['localStorage'],
      },
    });

  return i18n;
}

export { i18n };
