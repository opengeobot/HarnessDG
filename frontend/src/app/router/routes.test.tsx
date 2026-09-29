/**
 * 功能: 路由表结构回归测试（TASK-P0BR-066 / D-05）。
 *       065 复核发现 admin 子路由中 role-bindings 与 resource-acls 各被注册了两次，
 *       React Router 首个匹配生效导致重复注册成为口径漂移的温床。
 *       断言基于真实的 `router.routes` 对象递归展开，而非源码文本匹配。
 * 时间: 2026-09-28
 * 作者: AxeXie
 */
import { describe, it, expect } from 'vitest';
import { router } from './routes';

interface RouteNode {
  path?: string;
  element?: unknown;
  children?: unknown;
}

/** 递归展开为完整路径列表（子路由相对父级前缀） */
function collectFullPaths(routes: readonly RouteNode[], prefix = ''): string[] {
  const out: string[] = [];
  for (const route of routes) {
    let full = prefix;
    if (route.path) {
      full = route.path.startsWith('/')
        ? route.path
        : `${prefix === '/' ? '' : prefix}/${route.path}`;
      out.push(full);
    }
    if (Array.isArray(route.children)) {
      out.push(...collectFullPaths(route.children as RouteNode[], full || prefix));
    }
  }
  return out;
}

describe('路由表唯一注册（TASK-P0BR-066 / D-05）', () => {
  const paths = collectFullPaths(router.routes);

  it('任何路径不得重复注册（含 admin 全量子路由）', () => {
    const seen = new Map<string, number>();
    for (const p of paths) {
      seen.set(p, (seen.get(p) ?? 0) + 1);
    }
    const duplicated = [...seen.entries()].filter(([, count]) => count > 1).map(([p]) => p);
    expect(duplicated, `以下路由被重复注册: ${duplicated.join(', ')}`).toEqual([]);
  });

  it('/access 守卫口径与后端契约 listPats 一致（token:create）', () => {
    // 后端 PatApplicationService.listOwnPats 要求 TOKEN_CREATE；
    // OpenAPI GET /tokens 的 x-required-permissions 为 [token:create]。
    // 前端守卫若回退到 authorization:read 会拦截合法主体，此断言锁定口径。
    const accessRoute = router.routes
      .flatMap((r) => (Array.isArray(r.children) ? (r.children as RouteNode[]) : []))
      .find((r) => r.path === 'access');
    expect(accessRoute).toBeDefined();
    // guarded() 包裹后 element 是 RouteGuard 元素，直接检查其 props.requiredScopes。
    const element = accessRoute?.element as { props?: { requiredScopes?: string[] } } | undefined;
    expect(element?.props?.requiredScopes).toEqual(['token:create']);
  });
});
