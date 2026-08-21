import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // 业务 API 代理到 Spring Boot 后端（默认 8080）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 对象存储预签名 URL 代理（MinIO 默认 9000）
      '/objects': {
        target: 'http://localhost:9000',
        changeOrigin: true,
      },
    },
  },
});
