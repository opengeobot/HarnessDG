package com.aihub.integration.gitea.api;

import com.aihub.integration.gitea.application.WebhookInboxApplicationService;
import com.aihub.integration.gitea.application.WebhookInboxApplicationService.ReceiveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gitea Webhook Inbox 控制器（适配器）。
 *
 * <p>接收 Gitea Webhook 推送，委托 {@link WebhookInboxApplicationService} 处理，
 * 根据结果返回对应 HTTP 状态码。控制器不包含业务逻辑。
 */
@RestController
@RequestMapping("/api/v1/webhooks/gitea")
public class WebhookInboxController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookInboxController.class);

    private final WebhookInboxApplicationService webhookInboxService;

    public WebhookInboxController(WebhookInboxApplicationService webhookInboxService) {
        this.webhookInboxService = webhookInboxService;
    }

    /**
     * 接收 Gitea Webhook。
     *
     * <p>委托服务处理，根据结果映射 HTTP 状态码。
     */
    @PostMapping
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(value = "X-Gitea-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Gitea-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (deliveryId == null || eventType == null) {
            return ResponseEntity.badRequest().build();
        }

        ReceiveResult result = webhookInboxService.receive(deliveryId, eventType, signature, rawBody);

        return switch (result) {
            case ACCEPTED -> ResponseEntity.accepted().build();
            case UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case BAD_REQUEST -> ResponseEntity.badRequest().build();
            case INTERNAL_ERROR -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        };
    }
}
