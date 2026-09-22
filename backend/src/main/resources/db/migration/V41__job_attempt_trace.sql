-- ----------------------------------------------------------------------------
-- 功能: job_attempt 补齐追踪关联与尝试序号唯一约束。
-- 时间: 2026-09-20
-- 作者: AxeXie
-- 说明: 前向迁移。job_attempt 原先既无 trace_id 列，也无 (job_id, attempt_no)
--       唯一约束，导致 attempt 无法按 trace 关联，且被强杀的尝试重新领取后会复用
--       同一个 attempt_no（违反 INV-JOB-005「attempt 单调递增」）。本迁移补齐两者。
--       不修改 V9__job.sql 等已应用迁移。
-- ----------------------------------------------------------------------------

-- 1. trace_id：与 job_task.trace_id 同语义，用于 attempt 级追踪关联。
ALTER TABLE job_attempt
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

COMMENT ON COLUMN job_attempt.trace_id IS '分布式追踪 ID，与 job_task.trace_id 同语义';

-- 2. 存量消歧：唯一约束建立前，按确定规则（id 升序）把重复的 (job_id, attempt_no)
--    重新编号到该 job 现有最大 attempt_no 之上，不使用随机值。
WITH numbered AS (
    SELECT id,
           job_id,
           ROW_NUMBER() OVER (PARTITION BY job_id, attempt_no ORDER BY id) AS duplicate_rank
    FROM job_attempt
),
max_per_job AS (
    SELECT job_id, MAX(attempt_no) AS max_attempt_no
    FROM job_attempt
    GROUP BY job_id
),
shifting AS (
    SELECT n.id,
           m.max_attempt_no
               + ROW_NUMBER() OVER (PARTITION BY n.job_id ORDER BY n.id) AS new_attempt_no
    FROM numbered n
    JOIN max_per_job m ON m.job_id = n.job_id
    WHERE n.duplicate_rank > 1
)
UPDATE job_attempt a
SET attempt_no = s.new_attempt_no
FROM shifting s
WHERE a.id = s.id;

-- 3. 唯一约束：强制 attempt 序号在单个任务内单调且不重复。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_job_attempt_job_no'
    ) THEN
        ALTER TABLE job_attempt
            ADD CONSTRAINT uq_job_attempt_job_no UNIQUE (job_id, attempt_no);
    END IF;
END $$;

COMMENT ON CONSTRAINT uq_job_attempt_job_no ON job_attempt
    IS '同一任务内 attempt 序号唯一，保证 INV-JOB-005 的单调递增语义';
