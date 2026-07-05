-- ============================================================================
-- 功能: P2 版本/传输/预览 Schema。
--       创建 asset_version / version_artifact / upload_session /
--       upload_file / upload_part / asset_preview 表。
-- 时间: 2026-07-04
-- 作者: AxeXie
-- ============================================================================

-- ===========================================================================
-- asset_version: 资产版本记录
-- ===========================================================================

CREATE TABLE IF NOT EXISTS asset_version (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id      VARCHAR(40)  NOT NULL,
    asset_id        VARCHAR(40)  NOT NULL,
    version         VARCHAR(64)  NOT NULL,
    status          VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    source_commit   VARCHAR(64),
    manifest_digest VARCHAR(128),
    git_tag         VARCHAR(128),
    published_at    TIMESTAMPTZ,
    published_by    VARCHAR(40),
    notes           TEXT,
    row_version     BIGINT       NOT NULL DEFAULT 1,
    created_by      VARCHAR(40)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_asset_version_status CHECK (status IN (
        'DRAFT', 'VALIDATING', 'PENDING_REVIEW', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'
    )),
    CONSTRAINT ck_asset_version_version_id CHECK (version_id LIKE 'ver_%'),
    CONSTRAINT ck_asset_version_asset_id CHECK (asset_id LIKE 'ast_%'),
    CONSTRAINT ux_asset_version_coordinate UNIQUE (asset_id, version)
);

CREATE INDEX IF NOT EXISTS ix_asset_version_asset ON asset_version (asset_id);
CREATE INDEX IF NOT EXISTS ix_asset_version_status ON asset_version (status) WHERE status NOT IN ('PUBLISHED', 'ARCHIVED');

COMMENT ON TABLE asset_version IS '资产版本记录，每个资产可拥有多个版本，状态机 DRAFT → VALIDATING → PENDING_REVIEW → PUBLISHED → DEPRECATED → ARCHIVED';
COMMENT ON COLUMN asset_version.version_id IS '版本业务 ID（ver_ 前缀）';
COMMENT ON COLUMN asset_version.asset_id IS '所属资产 ID';
COMMENT ON COLUMN asset_version.version IS '版本字面量（如 v1.0.0）';
COMMENT ON COLUMN asset_version.status IS '版本状态：DRAFT/VALIDATING/PENDING_REVIEW/PUBLISHED/DEPRECATED/ARCHIVED';
COMMENT ON COLUMN asset_version.source_commit IS '版本对应的 Gitea Commit SHA';
COMMENT ON COLUMN asset_version.manifest_digest IS 'Manifest SHA-256 摘要';
COMMENT ON COLUMN asset_version.git_tag IS '发布时的 Git Tag 名';
COMMENT ON COLUMN asset_version.row_version IS '乐观锁版本号';

-- ===========================================================================
-- version_artifact: 版本内的工件文件
-- ===========================================================================

CREATE TABLE IF NOT EXISTS version_artifact (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artifact_id     VARCHAR(40)  NOT NULL,
    version_id      VARCHAR(40)  NOT NULL,
    path            TEXT         NOT NULL,
    dvc_file        TEXT,
    dvc_hash        VARCHAR(128),
    sha256          VARCHAR(64)  NOT NULL,
    size            BIGINT       NOT NULL DEFAULT 0,
    media_type      VARCHAR(128),

    CONSTRAINT ck_version_artifact_artifact_id CHECK (artifact_id LIKE 'art_%'),
    CONSTRAINT ck_version_artifact_version_id CHECK (version_id LIKE 'ver_%'),
    CONSTRAINT ck_version_artifact_size CHECK (size >= 0)
);

CREATE INDEX IF NOT EXISTS ix_version_artifact_version ON version_artifact (version_id);

COMMENT ON TABLE version_artifact IS '版本工件文件，每个文件包含路径、DVC 哈希与 SHA-256';
COMMENT ON COLUMN version_artifact.artifact_id IS '工件业务 ID（art_ 前缀）';
COMMENT ON COLUMN version_artifact.version_id IS '所属版本 ID';
COMMENT ON COLUMN version_artifact.path IS '工件相对路径';
COMMENT ON COLUMN version_artifact.dvc_file IS '对应的 .dvc 文件路径';
COMMENT ON COLUMN version_artifact.dvc_hash IS 'DVC 内容哈希';
COMMENT ON COLUMN version_artifact.sha256 IS '文件 SHA-256 摘要';

-- ===========================================================================
-- upload_session: 上传会话
-- ===========================================================================

CREATE TABLE IF NOT EXISTS upload_session (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id        VARCHAR(40)  NOT NULL,
    asset_id          VARCHAR(40)  NOT NULL,
    version_id        VARCHAR(40)  NOT NULL,
    principal_id      VARCHAR(40)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    total_bytes       BIGINT       NOT NULL DEFAULT 0,
    file_count        INT          NOT NULL DEFAULT 0,
    expires_at        TIMESTAMPTZ  NOT NULL,
    minio_upload_id   VARCHAR(256),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_upload_session_status CHECK (status IN ('OPEN', 'COMMITTING', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_upload_session_session_id CHECK (session_id LIKE 'upl_%'),
    CONSTRAINT ck_upload_session_asset_id CHECK (asset_id LIKE 'ast_%'),
    CONSTRAINT ck_upload_session_version_id CHECK (version_id LIKE 'ver_%'),
    CONSTRAINT ck_upload_session_bytes CHECK (total_bytes >= 0),
    CONSTRAINT ck_upload_session_files CHECK (file_count >= 0)
);

CREATE INDEX IF NOT EXISTS ix_upload_session_asset ON upload_session (asset_id);
CREATE INDEX IF NOT EXISTS ix_upload_session_status_expires
    ON upload_session (status, expires_at) WHERE status = 'OPEN';

COMMENT ON TABLE upload_session IS '上传会话，追踪 Multipart 上传生命周期';
COMMENT ON COLUMN upload_session.session_id IS '会话业务 ID（upl_ 前缀）';
COMMENT ON COLUMN upload_session.status IS '会话状态：OPEN/COMMITTING/COMPLETED/CANCELLED/EXPIRED';
COMMENT ON COLUMN upload_session.total_bytes IS '本次会话声明的总字节数';
COMMENT ON COLUMN upload_session.expires_at IS '会话过期时间（超时自动取消）';

-- ===========================================================================
-- upload_file: 上传文件（一个 session 内多个文件）
-- ===========================================================================

CREATE TABLE IF NOT EXISTS upload_file (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_id         VARCHAR(40)  NOT NULL,
    session_id      VARCHAR(40)  NOT NULL,
    path            TEXT         NOT NULL,
    size            BIGINT       NOT NULL DEFAULT 0,
    sha256          VARCHAR(64),
    media_type      VARCHAR(128),
    part_count      INT          NOT NULL DEFAULT 1,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',

    CONSTRAINT ck_upload_file_file_id CHECK (file_id LIKE 'upf_%'),
    CONSTRAINT ck_upload_file_session_id CHECK (session_id LIKE 'upl_%'),
    CONSTRAINT ck_upload_file_size CHECK (size >= 0),
    CONSTRAINT ck_upload_file_status CHECK (status IN ('PENDING', 'UPLOADING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_upload_file_session ON upload_file (session_id);

COMMENT ON TABLE upload_file IS '上传文件记录，跟踪单文件在会话内的状态';
COMMENT ON COLUMN upload_file.file_id IS '文件业务 ID（upf_ 前缀）';

-- ===========================================================================
-- upload_part: 文件分片（大文件 Multipart 分块）
-- ===========================================================================

CREATE TABLE IF NOT EXISTS upload_part (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_id         VARCHAR(40)  NOT NULL,
    part_number     INT          NOT NULL,
    size            BIGINT       NOT NULL DEFAULT 0,
    etag            VARCHAR(128),
    presigned_url   TEXT,
    uploaded_at     TIMESTAMPTZ,

    CONSTRAINT ck_upload_part_file_id CHECK (file_id LIKE 'upf_%'),
    CONSTRAINT ck_upload_part_size CHECK (size >= 0),
    CONSTRAINT ux_upload_part_file_part UNIQUE (file_id, part_number)
);

CREATE INDEX IF NOT EXISTS ix_upload_part_file ON upload_part (file_id);

COMMENT ON TABLE upload_part IS '文件分片，支持 Multipart 断点续传';
COMMENT ON COLUMN upload_part.part_number IS '分片编号（1-based）';
COMMENT ON COLUMN upload_part.presigned_url IS 'MinIO 预签名上传 URL（临时，不入日志）';

-- ===========================================================================
-- asset_preview: 资产预览（Card/README 渲染缓存）
-- ===========================================================================

CREATE TABLE IF NOT EXISTS asset_preview (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    preview_id      VARCHAR(40)  NOT NULL,
    asset_id        VARCHAR(40)  NOT NULL,
    version_id      VARCHAR(40),
    content_type    VARCHAR(64)  NOT NULL DEFAULT 'text/markdown',
    content         TEXT,
    rendered_html   TEXT,
    source_commit   VARCHAR(64),
    generated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_asset_preview_preview_id CHECK (preview_id LIKE 'prv_%'),
    CONSTRAINT ck_asset_preview_asset_id CHECK (asset_id LIKE 'ast_%')
);

CREATE INDEX IF NOT EXISTS ix_asset_preview_asset ON asset_preview (asset_id);
CREATE INDEX IF NOT EXISTS ix_asset_preview_version ON asset_preview (version_id) WHERE version_id IS NOT NULL;

COMMENT ON TABLE asset_preview IS '资产预览缓存，存储渲染后的 HTML（untrustedContent，前端需 DOMPurify）';
COMMENT ON COLUMN asset_preview.preview_id IS '预览业务 ID（prv_ 前缀）';
COMMENT ON COLUMN asset_preview.content_type IS '内容类型（如 text/markdown / text/html）';
COMMENT ON COLUMN asset_preview.rendered_html IS '渲染后 HTML（untrustedContent）';
