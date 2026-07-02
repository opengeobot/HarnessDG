/**
 * 功能: 路由权限守卫。未认证跳 /login（记录来源）；会话初始化中显示加载；
 *       已认证但缺少所需 Scope 时展示 403。前端守卫仅做体验提示，真实鉴权在后端。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Button, Result, Spin } from 'antd';
import { useAuth } from '@/app/auth';
import type { Scope } from '@/shared/types';
import { usePermission } from './usePermission';

export interface RouteGuardProps {
  /** 访问该路由所需 Scope，未提供则仅要求已认证 */
  requiredScopes?: Scope[];
  children: ReactNode;
}

export function RouteGuard({ requiredScopes, children }: RouteGuardProps) {
  const { status } = useAuth();
  const { hasAllScopes } = usePermission();
  const location = useLocation();

  if (status === 'initializing') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 96 }}>
        <Spin size="large" tip="正在恢复会话..." />
      </div>
    );
  }

  if (status === 'anonymous') {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    );
  }

  const allowed = !requiredScopes || hasAllScopes(requiredScopes);
  if (allowed) {
    return <>{children}</>;
  }

  return (
    <Result
      status="403"
      title="缺少访问权限"
      subTitle={`所需权限: ${requiredScopes?.join('、')}。请联系管理员申请相应权限。`}
      extra={
        <Button type="primary" disabled>
          申请权限
        </Button>
      }
    />
  );
}
