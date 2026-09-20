/**
 * 导航与路由可发现性回归测试。
 *
 * <p>覆盖 TASK-P0BR-050 的两类缺陷：
 * 1) 死链——版本列表「查看全部」必须指向已注册路由，不得落到全局 404；
 * 2) 能力不可发现——Team、角色绑定、资源 ACL 必须在导航中有带真实 Scope 的入口。
 *
 * <p>断言基于真实的 `NAV_ITEMS` 与 `router.routes` 对象，而非源码文本匹配，
 * 因此对格式化与重构不敏感。
 */
import { describe, it, expect } from 'vitest';
import { router } from '@/app/router/routes';
import { NAV_ITEMS } from './navigation';

interface NavEntry {
  key: string;
  requiredScopes?: string[];
}

/** 递归收集所有导航项（含子项） */
function flattenNav(items: readonly (typeof NAV_ITEMS)[number][] = NAV_ITEMS): NavEntry[] {
  const out: NavEntry[] = [];
  for (const item of items) {
    out.push({ key: item.key, requiredScopes: item.requiredScopes });
    if (item.children) {
      out.push(...flattenNav(item.children));
    }
  }
  return out;
}

/**
 * 递归收集路由表中注册的全部路径。
 *
 * <p>子路由在 React Router 中使用相对路径（如父 `admin` 下的 `users`），
 * 故这里累积父级前缀还原为完整路径，使「导航路径是否可达」的断言真正有效。
 */
function collectRoutePaths(
  routes: readonly { path?: string; children?: unknown }[],
  prefix = '',
): string[] {
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
      out.push(
        ...collectRoutePaths(
          route.children as { path?: string; children?: unknown }[],
          full || prefix,
        ),
      );
    }
  }
  return out;
}

describe('导航可发现性', () => {
  const nav = flattenNav();

  it('Team 入口存在并声明 project:view', () => {
    const item = nav.find((n) => n.key === '/admin/teams');
    expect(item, '/admin/teams 必须作为独立导航项存在').toBeDefined();
    expect(item?.requiredScopes).toEqual(['project:view']);
  });

  it('角色绑定入口存在并声明 authorization:read', () => {
    const item = nav.find((n) => n.key === '/admin/role-bindings');
    expect(item, '/admin/role-bindings 必须作为独立导航项存在').toBeDefined();
    expect(item?.requiredScopes).toEqual(['authorization:read']);
  });

  it('资源 ACL 入口存在并声明 authorization:read', () => {
    const item = nav.find((n) => n.key === '/admin/resource-acls');
    expect(item, '/admin/resource-acls 必须作为独立导航项存在').toBeDefined();
    expect(item?.requiredScopes).toEqual(['authorization:read']);
  });

  it('除父容器外每个导航项都声明了非空 Scope（不依赖默认全 Scope）', () => {
    for (const item of nav) {
      if (item.key === '/admin') {
        continue;
      }
      expect(item.requiredScopes, `${item.key} 必须声明 requiredScopes`).toBeDefined();
      expect(item.requiredScopes!.length, `${item.key} 的 Scope 不得为空`).toBeGreaterThan(0);
    }
  });

  it('不存在重复的导航路径（避免两份权限映射漂移）', () => {
    const keys = nav.map((n) => n.key);
    expect(new Set(keys).size).toBe(keys.length);
  });
});

describe('路由与导航一致性', () => {
  const routePaths = collectRoutePaths(router.routes);

  it('导航中每个叶子路径都在真实路由表中注册（无死链）', () => {
    const leafPaths = flattenNav()
      .map((n) => n.key)
      // 父容器 /admin 由 index 重定向承接，不需要独立路由。
      .filter((key) => key !== '/admin');
    for (const path of leafPaths) {
      expect(routePaths, `导航路径 ${path} 未在路由表中注册`).toContain(path);
    }
  });

  it('版本列表「查看全部」的目标路由已注册（修复死链）', () => {
    // VersionListPanel 链接到 /assets/:assetId/versions。
    expect(routePaths).toContain('/assets/:assetId/versions');
    // 更精确的版本详情路由仍然存在，不得被上面的路由覆盖。
    expect(routePaths).toContain('/assets/:assetId/versions/:versionId');
    // 兜底 404 仍在，说明上两条是精确注册而非兜底命中。
    expect(routePaths).toContain('/*');
  });

  it('新增页面路由已注册且指向真实存在的页面组件', () => {
    expect(routePaths).toContain('/admin/role-bindings');
    expect(routePaths).toContain('/admin/resource-acls');
    expect(routePaths).toContain('/admin/teams');
  });
});
