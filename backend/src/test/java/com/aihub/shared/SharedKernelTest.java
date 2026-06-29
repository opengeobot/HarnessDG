/*
 * 功能: shared-kernel 公共能力单元测试——验证 ID 生成、上下文与错误码契约（无需外部依赖）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.id.UlidIdGenerator;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * shared-kernel 离线单元测试，保证核心公共能力在无 Docker/数据库环境下亦被验证。
 */
class SharedKernelTest {

    private final IdGenerator idGenerator = new UlidIdGenerator();

    @AfterEach
    void cleanup() {
        PrincipalContextHolder.clear();
    }

    @Test
    void generatesPrefixedUlid() {
        String assetId = idGenerator.generate(IdPrefix.ASSET);
        assertThat(assetId).startsWith("ast_");
        assertThat(assetId).hasSize("ast_".length() + 26);
        assertThat(idGenerator.generate(IdPrefix.JOB)).startsWith("job_");
    }

    @Test
    void rejectsBlankPrefix() {
        assertThatThrownBy(() -> idGenerator.generate(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void principalContextHolderIsThreadScoped() {
        PrincipalContext context = new PrincipalContext(
                "usr_01", PrincipalType.USER, "subject", "org_01",
                List.of("prj_01"), Set.of("ADMIN"), Set.of("asset:read"),
                3, "zh-CN", "req_01", "trace01");
        PrincipalContextHolder.set(context);

        assertThat(PrincipalContextHolder.require().principalId()).isEqualTo("usr_01");
        PrincipalContextHolder.clear();
        assertThat(PrincipalContextHolder.current()).isEmpty();
    }

    @Test
    void errorCodeCarriesHttpStatusAndRetryability() {
        assertThat(ErrorCode.GITEA_DEPENDENCY_UNAVAILABLE.retryable()).isTrue();
        assertThat(ErrorCode.ASSET_VERSION_CONFLICT.httpStatus().value()).isEqualTo(409);

        ConflictException ex = new ConflictException(ErrorCode.ASSET_VERSION_CONFLICT, "version exists");
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.ASSET_VERSION_CONFLICT);
        assertThat(ex.details()).isEmpty();
    }
}
