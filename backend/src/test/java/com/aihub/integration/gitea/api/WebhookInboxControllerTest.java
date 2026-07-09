/*
 * 功能: WebhookInboxController 适配器测试——委托 Service 并正确映射 HTTP 状态码。
 * 时间: 2026-07-08
 */
package com.aihub.integration.gitea.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.integration.gitea.application.WebhookInboxApplicationService;
import com.aihub.integration.gitea.application.WebhookInboxApplicationService.ReceiveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebhookInboxControllerTest {

    private MockMvc mockMvc;
    private WebhookInboxApplicationService service;

    @BeforeEach
    void setUp() {
        service = mock(WebhookInboxApplicationService.class);
        var controller = new WebhookInboxController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnAcceptedWhenServiceAccepts() throws Exception {
        when(service.receive(any(), any(), any(), any())).thenReturn(ReceiveResult.ACCEPTED);
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-1")
                        .header("X-Gitea-Event", "push")
                        .content("{\"ref\":\"refs/heads/main\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void returnUnauthorizedWhenServiceRejects() throws Exception {
        when(service.receive(any(), any(), any(), any())).thenReturn(ReceiveResult.UNAUTHORIZED);
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-2")
                        .header("X-Gitea-Event", "push")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnBadRequestWhenServiceReturnsBadRequest() throws Exception {
        when(service.receive(any(), any(), any(), any())).thenReturn(ReceiveResult.BAD_REQUEST);
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-3")
                        .header("X-Gitea-Event", "push")
                        .content("not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnInternalServerErrorWhenServiceFails() throws Exception {
        when(service.receive(any(), any(), any(), any())).thenReturn(ReceiveResult.INTERNAL_ERROR);
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-4")
                        .header("X-Gitea-Event", "push")
                        .content("{}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void returnBadRequestWhenDeliveryIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Event", "push")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnBadRequestWhenEventTypeMissing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-6")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
