/**
 * 功能: 404 占位页面
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="您访问的页面不存在或已被移动。"
      extra={
        <Button type="primary" onClick={() => navigate('/assets')}>
          返回资产目录
        </Button>
      }
    />
  );
}
