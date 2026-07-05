-- ============================================================================
-- 功能: Team 领域资源建表（DEC-011: Team 是正式领域资源）。
--       team 从属于 organization，team_member 关联 principal。
-- 时间: 2026-07-05
-- 作者: AxeXie
-- ============================================================================

CREATE TABLE team (
    id            BIGSERIAL    PRIMARY KEY,
    team_id       VARCHAR(32)  NOT NULL UNIQUE,
    organization_id VARCHAR(32) NOT NULL REFERENCES organization(organization_id),
    name          VARCHAR(128) NOT NULL,
    description   TEXT,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(32),
    row_version   INTEGER      NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(organization_id, name)
);

CREATE TABLE team_member (
    id            BIGSERIAL    PRIMARY KEY,
    team_id       VARCHAR(32)  NOT NULL REFERENCES team(team_id),
    principal_id  VARCHAR(32)  NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
    created_by    VARCHAR(32),
    joined_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(team_id, principal_id)
);

CREATE INDEX idx_team_org ON team(organization_id);
CREATE INDEX idx_team_member_principal ON team_member(principal_id);
