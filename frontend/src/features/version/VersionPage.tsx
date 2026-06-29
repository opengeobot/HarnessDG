/**
 * 功能: 版本中心占位页面 (版本、Diff、发布、回滚视图)。骨架阶段无业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { PlaceholderPage } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';

export function VersionPage() {
  useDocumentTitle('版本中心');
  return (
    <PlaceholderPage
      title="版本中心"
      description="版本、Diff、发布与回滚视图将在 P1 实现。"
    />
  );
}
