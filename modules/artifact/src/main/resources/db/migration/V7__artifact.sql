-- V7__artifact.sql
-- artifact 域核心表（05 章）：对象 blob（租户去重/扫描/引用计数）、文件版本（content_source 二选一）、
-- 上传会话状态机（05 §5 十一态）+ 分片投影、下载会话（04 §6.5 下载计数事实）。
-- 对象键一律服务端生成 objects/{publicId}（05 §4），用户路径只存在 file_versions。

-- ---------- 对象 blob（05 §4/§7） ----------
CREATE TABLE object_blobs (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID UNIQUE NOT NULL,
    namespace_id        BIGINT NOT NULL REFERENCES namespaces(id),
    sha256              VARCHAR(64) NOT NULL,
    size_bytes          BIGINT NOT NULL CHECK (size_bytes >= 0),
    bucket              VARCHAR(64) NOT NULL DEFAULT 'artifacts',
    object_key          VARCHAR(256) NOT NULL,
    content_type        VARCHAR(128),
    -- scan_status 未 clean 前对象保持隔离，不可下载或预览（05 §11）
    scan_status         VARCHAR(16) NOT NULL DEFAULT 'pending'
                        CHECK (scan_status IN ('pending', 'clean', 'rejected', 'error')),
    scan_policy_version INT NOT NULL DEFAULT 1,
    status              VARCHAR(16) NOT NULL DEFAULT 'quarantined'
                        CHECK (status IN ('quarantined', 'available', 'pending_delete', 'deleted')),
    -- ref_count 由事务维护，仅作回收候选优化（05 §7）
    ref_count           INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 去重默认限定同一 tenant_scope（05 §7）
    UNIQUE (namespace_id, sha256)
);
CREATE INDEX ix_object_blobs_status ON object_blobs(status) WHERE status <> 'deleted';

-- ---------- 文件版本（05 §6.3：git/object 二选一约束） ----------
CREATE TABLE file_versions (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID UNIQUE NOT NULL,
    repository_id       BIGINT NOT NULL REFERENCES repositories(id),
    branch              VARCHAR(128) NOT NULL,
    path                TEXT NOT NULL,
    size_bytes          BIGINT NOT NULL CHECK (size_bytes >= 0),
    content_type        VARCHAR(128),
    content_source      VARCHAR(8) NOT NULL CHECK (content_source IN ('git', 'object')),
    git_blob_sha        VARCHAR(64),
    object_blob_id      BIGINT REFERENCES object_blobs(id),
    commit_sha          VARCHAR(64),
    status              VARCHAR(16) NOT NULL DEFAULT 'staging'
                        CHECK (status IN ('staging', 'active', 'deleting', 'deleted', 'failed', 'sync_error')),
    upload_session_id   BIGINT,
    created_by_user_id  BIGINT REFERENCES users(id),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 二选一真相约束（05 §6.3 末段）：对账发现两者并存或双缺标记 sync_error
    CONSTRAINT chk_file_content_source CHECK (
        (content_source = 'git' AND git_blob_sha IS NOT NULL AND object_blob_id IS NULL)
        OR (content_source = 'object' AND object_blob_id IS NOT NULL AND git_blob_sha IS NULL))
);
-- 同一 (repo, branch, path) 至多一个 staging/active 版本（历史 deleted 保留可引用）
CREATE UNIQUE INDEX uq_file_version_head
    ON file_versions(repository_id, branch, path)
    WHERE status IN ('staging', 'active');
CREATE INDEX ix_file_versions_repo ON file_versions(repository_id, branch, status, id);

-- ---------- 上传会话（05 §5/§6） ----------
CREATE TABLE upload_sessions (
    id                   BIGSERIAL PRIMARY KEY,
    public_id            UUID UNIQUE NOT NULL,
    repository_id        BIGINT NOT NULL REFERENCES repositories(id),
    branch               VARCHAR(128) NOT NULL,
    base_commit_sha      VARCHAR(64) NOT NULL,
    path                 TEXT NOT NULL,
    size_bytes           BIGINT NOT NULL CHECK (size_bytes >= 1),
    claimed_sha256       VARCHAR(64),
    content_type         VARCHAR(128),
    -- 初始化时由 filePolicy/path/类型/大小冻结，客户端不得切换（05 §3）
    content_source       VARCHAR(8) NOT NULL CHECK (content_source IN ('git', 'object')),
    status               VARCHAR(16) NOT NULL DEFAULT 'initiated'
                         CHECK (status IN ('initiated', 'uploading', 'verifying', 'scanning', 'committing',
                                           'conflict', 'completed', 'aborting', 'aborted', 'expired', 'failed')),
    part_size            BIGINT NOT NULL,
    max_concurrency      INT NOT NULL DEFAULT 3,
    expires_at           TIMESTAMPTZ NOT NULL,
    provider_upload_id   VARCHAR(128),
    -- 服务端预留对象键 objects/{publicId}，与 upload publicId 一致（05 §4）
    object_key           VARCHAR(256) NOT NULL,
    verified_sha256      VARCHAR(64),
    current_branch_head  VARCHAR(64),
    conflict_resolution  VARCHAR(32) NOT NULL DEFAULT 'fail_if_path_changed'
                         CHECK (conflict_resolution IN ('fail_if_path_changed', 'overwrite')),
    file_version_id      BIGINT REFERENCES file_versions(id),
    last_error           TEXT,
    version              BIGINT NOT NULL DEFAULT 0,
    created_by_user_id   BIGINT NOT NULL REFERENCES users(id),
    idempotency_key      VARCHAR(128),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_upload_idem
    ON upload_sessions(repository_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_upload_status ON upload_sessions(status)
    WHERE status IN ('initiated', 'uploading', 'verifying', 'scanning', 'committing', 'aborting');

-- 分片状态投影（05 §6.2：不得比对象存储 ListParts 更权威） ----------
CREATE TABLE upload_parts (
    upload_session_id BIGINT NOT NULL REFERENCES upload_sessions(id) ON DELETE CASCADE,
    part_number       INT NOT NULL,
    size_bytes        BIGINT NOT NULL,
    etag              VARCHAR(128),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (upload_session_id, part_number)
);

-- ---------- 下载会话（04 §6.5：签发即下载计数事实；Idempotency-Key 幂等） ----------
CREATE TABLE download_sessions (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID UNIQUE NOT NULL,
    repository_id   BIGINT NOT NULL REFERENCES repositories(id),
    file_version_id BIGINT NOT NULL REFERENCES file_versions(id),
    actor_user_id   BIGINT REFERENCES users(id),
    url             TEXT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_download_idem ON download_sessions(repository_id, idempotency_key);
CREATE INDEX ix_download_file ON download_sessions(file_version_id);
