/**
 * 功能: 占位页面组件。骨架阶段统一展示标题与 "P1 实现" 提示，不含业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { Result, Typography } from 'antd';
import { useTranslation } from 'react-i18next';

export interface PlaceholderPageProps {
  /** 页面标题 */
  title: string;
  /** 页面用途描述 */
  description?: string;
}

export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  const { t } = useTranslation();
  return (
    <Result
      status="info"
      title={title}
      subTitle={
        <Typography.Text type="secondary">
          {description ?? t('placeholder.defaultDescription')}
        </Typography.Text>
      }
    />
  );
}
