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
import zhApproval from '../locales/zh_CN/approval.json';
import zhQuality from '../locales/zh_CN/quality.json';
import zhLineage from '../locales/zh_CN/lineage.json';
import zhIngestion from '../locales/zh_CN/ingestion.json';
import zhReport from '../locales/zh_CN/report.json';
import zhDiagnosis from '../locales/zh_CN/diagnosis.json';
import zhQuery from '../locales/zh_CN/query.json';
import zhLogin from '../locales/zh_CN/login.json';

import enCommon from '../locales/en_US/common.json';
import enNavigation from '../locales/en_US/navigation.json';
import enTask from '../locales/en_US/task.json';
import enValidation from '../locales/en_US/validation.json';
import enDictionary from '../locales/en_US/dictionary.json';
import enSettings from '../locales/en_US/settings.json';
import enConfig from '../locales/en_US/config.json';
import enAudit from '../locales/en_US/audit.json';
import enApproval from '../locales/en_US/approval.json';
import enQuality from '../locales/en_US/quality.json';
import enLineage from '../locales/en_US/lineage.json';
import enIngestion from '../locales/en_US/ingestion.json';
import enReport from '../locales/en_US/report.json';
import enDiagnosis from '../locales/en_US/diagnosis.json';
import enQuery from '../locales/en_US/query.json';
import enLogin from '../locales/en_US/login.json';

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
          approval: zhApproval,
          quality: zhQuality,
          lineage: zhLineage,
          ingestion: zhIngestion,
          report: zhReport,
          diagnosis: zhDiagnosis,
          query: zhQuery,
          login: zhLogin,
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
          approval: enApproval,
          quality: enQuality,
          lineage: enLineage,
          ingestion: enIngestion,
          report: enReport,
          diagnosis: enDiagnosis,
          query: enQuery,
          login: enLogin,
        },
      },
      fallbackLng: 'zh_CN',
      defaultNS: 'common',
      ns: ['common', 'navigation', 'task', 'validation', 'dictionary', 'settings', 'config', 'audit',
        'approval', 'quality', 'lineage', 'ingestion', 'report', 'diagnosis', 'query', 'login'],
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
