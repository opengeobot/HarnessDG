/**
 * 功能: 上传中心占位页面 (分片直传、断点续传、校验)。骨架阶段无业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { PlaceholderPage } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';

export function UploadPage() {
  useDocumentTitle('上传中心');
  return (
    <PlaceholderPage
      title="上传中心"
      description="分片直传、断点续传与校验将在 P1 实现。"
    />
  );
}
