/**
 * 功能: 管理中心占位页面 (字典、存储、Webhook、任务)。骨架阶段无业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { PlaceholderPage } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';

export function AdminPage() {
  useDocumentTitle('管理中心');
  return (
    <PlaceholderPage
      title="管理中心"
      description="字典、存储、Webhook 与任务管理将在 P1 实现。"
    />
  );
}
