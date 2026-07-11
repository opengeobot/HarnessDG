/*
 * 功能: PrincipalEmailResolver 单元测试——principal_id 与直传邮箱解析。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PrincipalEmailResolverTest {

    @Test
    void resolvesEmailFromIamUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class), eq("prn_test_user")))
                .thenReturn(List.of("user@example.com"));
        PrincipalEmailResolver resolver = new PrincipalEmailResolver(jdbcTemplate);

        Optional<String> email = resolver.resolveEmail("prn_test_user");

        assertThat(email).contains("user@example.com");
    }

    @Test
    void acceptsDirectEmailAddress() {
        PrincipalEmailResolver resolver = new PrincipalEmailResolver(mock(JdbcTemplate.class));

        Optional<String> email = resolver.resolveEmail("direct@example.com");

        assertThat(email).contains("direct@example.com");
    }

    @Test
    void returnsEmptyWhenPrincipalHasNoEmail() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class), eq("prn_no_email")))
                .thenReturn(List.of());
        PrincipalEmailResolver resolver = new PrincipalEmailResolver(jdbcTemplate);

        Optional<String> email = resolver.resolveEmail("prn_no_email");

        assertThat(email).isEmpty();
    }
}
