// 仓库卡片（目录/我的列表复用）
// 时间：2026-08-21  作者：AxeXie
import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { Repository } from '../api/client';

const VIS_LABEL: Record<string, string> = {
  public: '公开', organization: '组织', private: '私有',
};

export default function RepoCard({ repo }: { repo: Repository }) {
  const nav = useNavigate();
  return (
    <div className="card repo-card" onClick={() => nav(`/repo/${repo.id}`)}>
      <div className="repo-card-top">
        <span className="repo-type">{repo.type}</span>
        <div style={{ display: 'flex', gap: 6 }}>
          <span className={`badge badge-${repo.visibility}`}>{VIS_LABEL[repo.visibility]}</span>
          {repo.gated && <span className="badge badge-gated">需申请</span>}
        </div>
      </div>
      <div className="repo-name">{repo.displayName || repo.name}</div>
      {repo.description && <div className="repo-desc">{repo.description}</div>}
      <div className="repo-foot">
        <span className="repo-ns">{repo.namespace}/{repo.name}</span>
        <span>♥ {repo.stats.likes}</span>
        <span>★ {repo.stats.favorites}</span>
        <span>↓ {repo.stats.downloads}</span>
      </div>
    </div>
  );
}
