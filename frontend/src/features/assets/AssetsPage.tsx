/**
 * 功能: 资产目录占位页面 (模型/数据集列表与详情)。骨架阶段无业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useEffect } from 'react';
import { PlaceholderPage } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { fetchSystemDependencies } from '@/shared/api';

export function AssetsPage() {
  useDocumentTitle('资产目录');

  // 示例: 演示页面经由统一 API Client 访问后端，禁止页面手写 fetch/拼 URL。
  // 骨架阶段不真正发起请求，仅保留契约引用，避免对未就绪后端产生副作用。
  useEffect(() => {
    const enabled = false;
    if (enabled) {
      void fetchSystemDependencies();
    }
  }, []);

  return (
    <PlaceholderPage
      title="资产目录"
      description="模型 / 数据集列表与详情将在 P1 实现。"
    />
  );
}
