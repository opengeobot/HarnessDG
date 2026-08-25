// 下载方式弹窗（09 §8.3）：CLI / SDK / Git 三方式 + 「跳过大文件」GIT_LFS_SKIP_SMUDGE + 复制
// 时间：2026-08-21  作者：AxeXie
import React, { useState } from 'react';
import type { Repository } from '../api/client';
import { useToast } from './ui';

const TABS = [
  { key: 'cli', label: 'CLI' },
  { key: 'sdk', label: 'SDK' },
  { key: 'git', label: 'Git' },
] as const;

type TabKey = typeof TABS[number]['key'];

export default function DownloadModal({ repo, onClose }: { repo: Repository; onClose: () => void }) {
  const [tab, setTab] = useState<TabKey>('cli');
  const [skipLfs, setSkipLfs] = useState(false);
  const toast = useToast();
  const path = `${repo.namespace}/${repo.name}`;

  const cmds: Record<TabKey, string[]> = {
    cli: [
      `modelhub download ${path}`,
      ...(skipLfs ? [`modelhub download ${path} --skip-lfs`] : []),
    ],
    sdk: [
      'from modelhub import snapshot_download',
      `repo = snapshot_download('${path}')`,
    ],
    git: [
      ...(skipLfs
        ? ['git clone https://git.modelhub.local/' + path + '.git # GIT_LFS_SKIP_SMUDGE=1']
        : ['git clone https://git.modelhub.local/' + path + '.git']),
    ],
  };

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      toast.show('已复制到剪贴板');
    } catch {
      toast.show('复制失败，请手动选择文本', 'err');
    }
  }

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" style={{ maxWidth: 620 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="modal-title">下载 {repo.displayName || repo.name}</span>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="tabs" style={{ marginTop: 0 }}>
            {TABS.map((t) => (
              <div key={t.key} className={`tab ${tab === t.key ? 'active' : ''}`}
                   onClick={() => setTab(t.key)}>{t.label}</div>
            ))}
          </div>

          <div className="dl-cmds">
            {cmds[tab].map((c) => (
              <div key={c} className="dl-cmd-row">
                <code className="dl-cmd">{c}</code>
                <button className="btn btn-ghost btn-sm" onClick={() => copy(c)}>复制</button>
              </div>
            ))}
          </div>

          <label className="filter-check" style={{ marginTop: 12 }}>
            <input type="checkbox" checked={skipLfs} onChange={(e) => setSkipLfs(e.target.checked)} />
            跳过大文件（Git LFS：GIT_LFS_SKIP_SMUDGE=1）
          </label>
          <div className="hint" style={{ marginTop: 8 }}>
            单文件下载请前往「文件」标签页，通过下载会话获取预签名链接。
          </div>
        </div>
      </div>
    </div>
  );
}
