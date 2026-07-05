/*
 * 功能: 讨论 REST 适配器 Web 切片测试——校验 DiscussionController / CommentController 端点路由与映射。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.asset.discussion.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.asset.discussion.application.CommentView;
import com.aihub.asset.discussion.application.DiscussionApplicationService;
import com.aihub.asset.discussion.application.ThreadView;
import com.aihub.asset.discussion.domain.CommentStatus;
import com.aihub.asset.discussion.domain.ThreadStatus;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.bootstrap.GlobalExceptionHandler;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.bootstrap.security.RestAccessDeniedHandler;
import com.aihub.bootstrap.security.RestAuthenticationEntryPoint;
import com.aihub.bootstrap.security.SecurityConfiguration;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link DiscussionController} 与 {@link CommentController} Web 切片测试。
 */
@WebMvcTest({DiscussionController.class, CommentController.class})
@Import({DiscussionController.class, CommentController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class,
        GlobalExceptionHandler.class, SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        AuthorizationService.class})
class DiscussionControllerTest {

    @Configuration
    static class TestConfig {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenSigner tokenSigner;
    @MockitoBean private DiscussionApplicationService discussionService;
    @MockitoBean private RoleBindingRepository roleBindingRepository;
    @MockitoBean private ResourceAclRepository resourceAclRepository;
    @MockitoBean private AgentToolRepository agentToolRepository;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of("asset:manage", "asset:read"));
    }

    private String bearer() {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_test", PrincipalType.USER, 0L, Set.of("asset:manage", "asset:read"), TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    private ThreadView threadView() {
        return new ThreadView("thr_001", "ast_001", "Test Thread", "prn_test",
                ThreadStatus.OPEN, 0, null, Instant.now());
    }

    private CommentView commentView() {
        return new CommentView("cmt_001", "thr_001", null, "ast_001",
                "Hello", "prn_test", CommentStatus.VISIBLE, 0, Instant.now(), Instant.now());
    }

    @Test
    void createThreadReturns201() throws Exception {
        given(discussionService.createThread(anyString(), anyString(), anyString()))
                .willReturn(threadView());

        mockMvc.perform(post("/api/v1/assets/ast_001/discussions")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Test Thread"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.threadId").value("thr_001"))
                .andExpect(jsonPath("$.data.title").value("Test Thread"));
    }

    @Test
    void listThreadsReturns200() throws Exception {
        CursorPage<ThreadView> page = new CursorPage<>(List.of(threadView()), null, false);
        given(discussionService.listThreads(anyString(), any(), anyInt())).willReturn(page);

        mockMvc.perform(get("/api/v1/assets/ast_001/discussions")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].threadId").value("thr_001"));
    }

    @Test
    void createCommentReturns201() throws Exception {
        given(discussionService.createComment(anyString(), any(), anyString(), anyString()))
                .willReturn(commentView());

        mockMvc.perform(post("/api/v1/discussions/thr_001/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Hello"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentId").value("cmt_001"))
                .andExpect(jsonPath("$.data.body").value("Hello"));
    }

    @Test
    void listCommentsReturns200() throws Exception {
        given(discussionService.listComments(anyString(), any(), anyInt()))
                .willReturn(List.of(commentView()));

        mockMvc.perform(get("/api/v1/discussions/thr_001/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commentId").value("cmt_001"));
    }

    @Test
    void retractCommentReturns200() throws Exception {
        CommentView retracted = new CommentView("cmt_001", "thr_001", null, "ast_001",
                "Hello", "prn_test", CommentStatus.RETRACTED, 0, Instant.now(), Instant.now());
        given(discussionService.retractComment(anyString(), anyString())).willReturn(retracted);

        mockMvc.perform(post("/api/v1/discussions/thr_001/comments/cmt_001/retract")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETRACTED"));
    }

    @Test
    void lockThreadReturns200() throws Exception {
        ThreadView locked = new ThreadView("thr_001", "ast_001", "Test Thread", "prn_test",
                ThreadStatus.LOCKED, 0, null, Instant.now());
        given(discussionService.lockThread(anyString(), anyString())).willReturn(locked);

        mockMvc.perform(post("/api/v1/discussions/thr_001/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
    }
}
