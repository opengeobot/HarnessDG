/**
 * 功能: 占位页面组件。骨架阶段统一展示标题与 "P1 实现" 提示，不含业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { Result, Typography } from 'antd';

export interface PlaceholderPageProps {
  /** 页面标题 */
  title: string;
  /** 页面用途描述 */
  description?: string;
}

export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <Result
      status="info"
      title={title}
      subTitle={
        <Typography.Text type="secondary">
          {description ?? '该页面为 P0 工程基线占位，业务逻辑将在 P1 实现。'}
        </Typography.Text>
      }
    />
  );
}
