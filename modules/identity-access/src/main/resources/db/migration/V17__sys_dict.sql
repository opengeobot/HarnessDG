-- V17__sys_dict.sql
-- 通用字典（管理后台计划 §二）：与管理面配套的杂项字典，独立于市场元数据 taxonomy。
-- 字典项标签按语言列存储，后期加语种 = 加列 + 迁移。

CREATE TABLE sys_dict (
    id          BIGSERIAL PRIMARY KEY,
    public_id   UUID UNIQUE NOT NULL,
    dict_code   CITEXT UNIQUE NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    status      VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sys_dict_item (
    id         BIGSERIAL PRIMARY KEY,
    dict_id    BIGINT NOT NULL REFERENCES sys_dict(id),
    item_value VARCHAR(128) NOT NULL,
    label_zh   VARCHAR(128) NOT NULL,
    label_en   VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status     VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    remark     VARCHAR(256),
    UNIQUE (dict_id, item_value)
);
CREATE INDEX ix_sys_dict_item_dict ON sys_dict_item(dict_id);

-- ---------- 示例种子：用户状态字典（供管理页消费演示） ----------
DO $$
DECLARE d BIGINT;
BEGIN
    INSERT INTO sys_dict (public_id, dict_code, name, description)
    VALUES (gen_random_uuid(), 'sys_user_status', '用户状态', '平台用户账户状态')
    RETURNING id INTO d;

    INSERT INTO sys_dict_item (dict_id, item_value, label_zh, label_en, sort_order) VALUES
    (d, 'active',   '正常', 'Active',   0),
    (d, 'locked',   '锁定', 'Locked',   1),
    (d, 'disabled', '禁用', 'Disabled', 2);
END $$;
