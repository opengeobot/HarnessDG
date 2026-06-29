/**
 * 功能: 路由权限守卫骨架。当缺少所需 Scope 时展示 403 占位（不泄露资产是否存在）。
 *       前端守卫仅做体验提示，真实鉴权在后端，遵循设计第 10.3、15.7 节。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import type { ReactNode } from 'react';
import { Button, Result } from 'antd';
import type { Scope } from '@/shared/types';
import { usePermission } from './usePermission';

export interface RouteGuardProps {
  /** 访问该路由所需 Scope，未提供则不限制 */
  requiredScopes?: Scope[];
  children: ReactNode;
}

export function RouteGuard({ requiredScopes, children }: RouteGuardProps) {
  const { hasAllScopes } = usePermission();
  const allowed = !requiredScopes || hasAllScopes(requiredScopes);

  if (allowed) {
    return <>{children}</>;
  }

  return (
    <Result
      status="403"
      title="缺少访问权限"
      subTitle={`所需权限: ${requiredScopes?.join('、')}。请联系管理员申请相应 Scope。`}
      extra={
        <Button type="primary" disabled>
          申请权限（P1 实现）
        </Button>
      }
    />
  );
}
