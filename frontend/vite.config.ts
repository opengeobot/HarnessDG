/**
 * 功能: Vite 构建与开发服务器配置 (路径别名、/api 代理占位)
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境将 /api 透传到后端，骨架阶段后端可不存在
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    exclude: ['**/node_modules/**', '**/dist/**', '**/e2e/**'],
  },
  build: {
    outDir: 'dist',
    // 构建产物目录与 SPA 业务路由 `/assets/:assetId...` 分名（TASK-P0BR-073）：
    // nginx 静态缓存 location 为 /static/，若沿用默认 assets 将遮蔽深链整页加载。
    // 必须与 frontend/nginx/default.conf 的 location 同步变更，改名不同步 = 全站白屏。
    assetsDir: 'static',
    sourcemap: false,
    // antd 为单一大依赖（约 970kB），已单独拆为 antd-vendor，无法进一步细分；
    // 主业务 chunk 已由 1.34MB 降至约 114kB，此处上调告警阈值以消除 antd 单包噪音。
    chunkSizeWarningLimit: 1100,
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'antd-vendor': ['antd', '@ant-design/icons'],
          'query-vendor': ['@tanstack/react-query'],
        },
      },
    },
  },
});
