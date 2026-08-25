// 文档页（09 §4.9）— 静态部署与功能说明
// 时间：2026-08-21  作者：AxeXie
import React from 'react';

export default function DocsPage() {
  return (
    <div className="card card-pad docs-page">
      <h1 style={{ marginTop: 0 }}>使用文档</h1>

      <h2>平台简介</h2>
      <p>
        ModelHub 是模型、数据集与工作空间的一体化社区平台：目录浏览、数据驱动筛选、
        全局搜索、下载分发（CLI / SDK / Git）、点赞收藏、申请制（gated）访问控制。
      </p>

      <h2>部署架构</h2>
      <ul>
        <li><b>app</b>：Spring Boot 单体（端口 8080），模块化分层 shared / identity-access / catalog / api / app。</li>
        <li><b>nginx</b>：反向代理 /api 与 /actuator（端口 8081）。</li>
        <li><b>postgres</b>：主存储（Flyway 迁移 + 种子数据）。</li>
        <li><b>redis</b>：幂等键与会话缓存。</li>
        <li><b>minio</b>：对象存储，预签名 URL 直连下载。</li>
        <li><b>gitea</b>：Git 仓库存储后端。</li>
      </ul>

      <h2>下载方式</h2>
      <pre className="readme-text">{`# CLI
modelhub download <namespace>/<name>

# SDK
from modelhub import snapshot_download
repo = snapshot_download('<namespace>/<name>')

# Git（跳过大文件：GIT_LFS_SKIP_SMUDGE=1）
git clone https://git.modelhub.local/<namespace>/<name>.git`}</pre>

      <h2>认证模型</h2>
      <ul>
        <li>注册/登录后获得 Access Token（内存态）+ Refresh Cookie（HttpOnly）+ CSRF 双提交令牌。</li>
        <li>Access Token 过期后前端自动经 /auth/refresh 续期一次。</li>
        <li>写操作携带 Idempotency-Key；条件更新携带 If-Match（ETag）。</li>
      </ul>

      <h2>申请制（gated）资源</h2>
      <p>
        标记为申请制的仓库需先提交访问申请，维护者审批通过后方可下载；
        审批结果可在个人中心「访问申请」中跟踪。
      </p>
    </div>
  );
}
