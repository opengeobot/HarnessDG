// AIHub CLI Vitest 配置
// 作者: AxeXie
// 创建时间: 2026-07-30

import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    include: ['test/**/*.test.ts'],
  },
});