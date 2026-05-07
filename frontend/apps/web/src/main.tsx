/**
 * 功能：应用入口文件
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import '@harnessdg/design-tokens/tokens.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
