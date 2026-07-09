/*
 * 功能: 讨论应用服务单元测试——校验线程/评论创建、编辑修订、锁线、Moderator 操作与授权前置。
 * 时间: 2026-07-04
 * 作者: AxeXie
 */
package com.aihub.asset.discussion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.asset.discussion.domain.Comment;
import com.aihub.asset.discussion.domain.CommentStatus;
import com.aihub.asset.discussion.domain.DiscussionRepository;
import com.aihub.asset.discussion.domain.DiscussionThread;
import com.aihub.asset.discussion.domain.ThreadStatus;
import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link DiscussionApplicationService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscussionApplicationServiceTest {

    @Mock private DiscussionRepository discussionRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private IdGenerator idGenerator;
    @Mock private com.aihub.notification.application.NotificationService notificationService;

    private DiscussionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DiscussionApplicationService(
                discussionRepository, authorizationService, auditService, idGenerator,
                notificationService);
        when(idGenerator.generate(any(IdPrefix.class))).thenReturn("gen_id");
    }

    private DiscussionThread openThread() {
        Instant now = Instant.now();
        return new DiscussionThread("thr_01", "ast_01", "测试线程", "usr_01",
                ThreadStatus.OPEN, 0, null, now, now);
    }

    private Comment visibleComment() {
        Instant now = Instant.now();
        return new Comment("cmt_01", "thr_01", null, "ast_01",
                "原始评论内容", "usr_01", CommentStatus.VISIBLE, 0, now, now);
    }

    @Test
    void createThreadInsertsAndAudits() {
        ThreadView view = service.createThread("ast_01", "测试线程", "usr_01");

        assertThat(view.threadId()).isEqualTo("gen_id");
        assertThat(view.title()).isEqualTo("测试线程");
        assertThat(view.status()).isEqualTo(ThreadStatus.OPEN);
        verify(authorizationService).requirePermission(Permissions.ASSET_DISCUSS);
        verify(discussionRepository).insertThread(any(DiscussionThread.class));
        verify(auditService).record(any());
    }

    @Test
    void createThreadRejectsBlankTitle() {
        assertThatThrownBy(() -> service.createThread("ast_01", "  ", "usr_01"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createCommentOnOpenThreadSucceeds() {
        DiscussionThread thread = openThread();
        when(discussionRepository.findThread("thr_01")).thenReturn(Optional.of(thread));

        CommentView view = service.createComment("thr_01", null, "评论内容", "usr_02");

        assertThat(view.commentId()).isEqualTo("gen_id");
        assertThat(view.body()).isEqualTo("评论内容");
        verify(discussionRepository).insertComment(any(Comment.class));
        verify(discussionRepository).updateThread(any(DiscussionThread.class));
        verify(auditService).record(any());
    }

    @Test
    void createCommentOnLockedThreadThrowsConflict() {
        Instant now = Instant.now();
        DiscussionThread lockedThread = new DiscussionThread("thr_01", "ast_01", "已锁",
                "usr_01", ThreadStatus.LOCKED, 0, null, now, now);
        when(discussionRepository.findThread("thr_01")).thenReturn(Optional.of(lockedThread));

        assertThatThrownBy(() -> service.createComment("thr_01", null, "内容", "usr_02"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createCommentRejectsBlankBody() {
        DiscussionThread thread = openThread();
        when(discussionRepository.findThread("thr_01")).thenReturn(Optional.of(thread));

        assertThatThrownBy(() -> service.createComment("thr_01", null, "  ", "usr_01"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void editCommentByAuthorSavesRevision() {
        Comment comment = visibleComment();
        when(discussionRepository.findComment("cmt_01")).thenReturn(Optional.of(comment));

        CommentView view = service.editComment("cmt_01", "修改后的内容", "usr_01");

        assertThat(view.body()).isEqualTo("修改后的内容");
        verify(discussionRepository).insertRevision(any(), any(), any(), any());
        verify(discussionRepository).updateComment(any(Comment.class));
    }

    @Test
    void editCommentByNonAuthorThrowsConflict() {
        Comment comment = visibleComment();
        when(discussionRepository.findComment("cmt_01")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.editComment("cmt_01", "新内容", "usr_other"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void retractCommentByAuthorSucceeds() {
        Comment comment = visibleComment();
        when(discussionRepository.findComment("cmt_01")).thenReturn(Optional.of(comment));

        CommentView view = service.retractComment("cmt_01", "usr_01");

        assertThat(view.status()).isEqualTo(CommentStatus.RETRACTED);
    }

    @Test
    void hideCommentRequiresModeratePermission() {
        Comment comment = visibleComment();
        when(discussionRepository.findComment("cmt_01")).thenReturn(Optional.of(comment));

        CommentView view = service.hideComment("cmt_01", "mod_01");

        assertThat(view.status()).isEqualTo(CommentStatus.HIDDEN);
        verify(authorizationService).requirePermission(Permissions.ASSET_MODERATE);
    }

    @Test
    void lockThreadTransitionsToLocked() {
        DiscussionThread thread = openThread();
        when(discussionRepository.findThread("thr_01")).thenReturn(Optional.of(thread));

        ThreadView view = service.lockThread("thr_01", "mod_01");

        assertThat(view.status()).isEqualTo(ThreadStatus.LOCKED);
        verify(authorizationService).requirePermission(Permissions.ASSET_MODERATE);
    }

    @Test
    void unlockLockedThreadTransitionsToOpen() {
        Instant now = Instant.now();
        DiscussionThread lockedThread = new DiscussionThread("thr_01", "ast_01", "已锁",
                "usr_01", ThreadStatus.LOCKED, 0, null, now, now);
        when(discussionRepository.findThread("thr_01")).thenReturn(Optional.of(lockedThread));

        ThreadView view = service.unlockThread("thr_01", "mod_01");

        assertThat(view.status()).isEqualTo(ThreadStatus.OPEN);
    }

    @Test
    void listThreadsRequiresReadPermission() {
        when(discussionRepository.listThreadsByAsset(anyString(), any(), anyInt()))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        service.listThreads("ast_01", null, 20);

        verify(authorizationService).requirePermission(Permissions.ASSET_READ);
    }

    @Test
    void listCommentsRequiresReadPermission() {
        DiscussionThread thread = openThread();
        when(discussionRepository.findThread("thr_01")).thenReturn(Optional.of(thread));
        when(discussionRepository.listCommentsByThread(anyString(), any(), anyInt()))
                .thenReturn(List.of());

        service.listComments("thr_01", null, 20);

        verify(authorizationService).requirePermission(Permissions.ASSET_READ);
    }
}
