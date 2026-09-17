/*
 * 功能: 幂等存储集成测试——以真实 PostgreSQL 验证 save/find 往返与时间列绑定。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencyRecord;
import com.aihub.shared.idempotency.IdempotencyStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 幂等存储集成测试。
 *
 * <p>历史缺陷：{@code JdbcIdempotencyStore.save} 以 {@code java.time.Instant} 绑定
 * {@code completed_at}(TIMESTAMPTZ)，pgjdbc 无法推断类型并抛
 * {@code Can't infer the SQL type to use for an instance of java.time.Instant}，
 * 导致所有携带 {@code Idempotency-Key} 的写端点返回 500。
 * 单元测试因 mock 掉存储层而无法发现，本测试在真实 PostgreSQL 上覆盖该路径。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class JdbcIdempotencyStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndReadsBackRecordWithCompletedAtBound() {
        IdempotencyKey key = new IdempotencyKey("idem-it-save-0001", "usr_it", "POST", "/api/v1/it");
        IdempotencyRecord record = new IdempotencyRecord(key, "digest-it-0001", 201, "{\"id\":\"x\"}",
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        assertThatCode(() -> idempotencyStore.save(record))
                .as("save 不得再抛 Can't infer the SQL type for java.time.Instant")
                .doesNotThrowAnyException();

        IdempotencyRecord stored = idempotencyStore.find(key).orElseThrow();
        assertThat(stored.responseStatus()).isEqualTo(201);
        assertThat(stored.responseBody()).isEqualTo("{\"id\":\"x\"}");
        assertThat(stored.requestFingerprint()).isEqualTo("digest-it-0001");
        assertThat(stored.key().principalId()).isEqualTo("usr_it");
    }

    @Test
    void writesCompletedAtIntoTheTimeColumn() {
        IdempotencyKey key = new IdempotencyKey("idem-it-completed-0001", "usr_it", "POST", "/api/v1/it");
        idempotencyStore.save(new IdempotencyRecord(key, "digest-it-0002", 200, "{}", Instant.now()));

        var completedAt = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM api_idempotency WHERE idempotency_key = ?",
                java.time.OffsetDateTime.class, key.key());

        assertThat(completedAt)
                .as("completed_at 必须真实写入，而不是因绑定失败而留空")
                .isNotNull();
    }

    @Test
    void scopeIncludesPrincipalSoAnotherPrincipalDoesNotHitRecord() {
        IdempotencyKey owner = new IdempotencyKey("idem-it-scope-0001", "usr_owner", "POST", "/api/v1/it");
        idempotencyStore.save(new IdempotencyRecord(owner, "digest-it-0003", 201, "owned", Instant.now()));

        IdempotencyKey other = new IdempotencyKey("idem-it-scope-0001", "usr_other", "POST", "/api/v1/it");

        assertThat(idempotencyStore.find(other))
                .as("同 key 不同 principal 不得命中他人记录")
                .isEmpty();
    }

    @Test
    void savingSameKeyTwiceKeepsSingleRowAndFirstResult() {
        IdempotencyKey key = new IdempotencyKey("idem-it-dupe-0001", "usr_it", "POST", "/api/v1/it");
        idempotencyStore.save(new IdempotencyRecord(key, "digest-first", 201, "first", Instant.now()));
        idempotencyStore.save(new IdempotencyRecord(key, "digest-second", 500, "second", Instant.now()));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_idempotency WHERE idempotency_key = ?",
                Integer.class, key.key());
        assertThat(rows).isEqualTo(1);

        IdempotencyRecord stored = idempotencyStore.find(key).orElseThrow();
        assertThat(stored.responseBody())
                .as("既有记录不得被覆盖")
                .isEqualTo("first");
    }
}
