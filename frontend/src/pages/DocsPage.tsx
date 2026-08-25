// 文档页（09 §4.9）— 静态部署与功能说明
// 时间：2026-08-21  作者：AxeXie
import React from 'react';
import { Trans, useTranslation } from 'react-i18next';

const ARCH_KEYS = ['docs.archApp', 'docs.archNginx', 'docs.archPostgres',
  'docs.archRedis', 'docs.archMinio', 'docs.archGitea'];

export default function DocsPage() {
  const { t } = useTranslation();
  return (
    <div className="card card-pad docs-page">
      <h1 style={{ marginTop: 0 }}>{t('docs.title')}</h1>

      <h2>{t('docs.introTitle')}</h2>
      <p>{t('docs.intro')}</p>

      <h2>{t('docs.archTitle')}</h2>
      <ul>
        {ARCH_KEYS.map((k) => <li key={k}><Trans i18nKey={k} /></li>)}
      </ul>

      <h2>{t('docs.downloadTitle')}</h2>
      <pre className="readme-text">{t('docs.downloadSample')}</pre>

      <h2>{t('docs.authTitle')}</h2>
      <ul>
        <li>{t('docs.auth1')}</li>
        <li>{t('docs.auth2')}</li>
        <li>{t('docs.auth3')}</li>
      </ul>

      <h2>{t('docs.gatedTitle')}</h2>
      <p>{t('docs.gated')}</p>
    </div>
  );
}
