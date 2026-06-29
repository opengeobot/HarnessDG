/**
 * 功能: 前端应用入口，挂载根组件
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@/app/App';
import 'antd/dist/reset.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('未找到根挂载节点 #root');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
