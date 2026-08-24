package com.modelhub.catalog.worker;

import com.modelhub.catalog.service.GatedAccessService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * gated grant 到期收敛（02 §4、04 §4）：
 * 周期调用 {@link GatedAccessService#expireOverdue()}，将 grant 已过期的
 * approved 申请置为 expired 终态。收敛仅推进 approved 申请，重复执行幂等。
 * 调度开关由 app 启动类统一 @EnableScheduling（与 OutboxPoller 相同）。
 */
@Component
public class GatedGrantExpiryWorker {

    private final GatedAccessService gatedAccessService;

    public GatedGrantExpiryWorker(GatedAccessService gatedAccessService) {
        this.gatedAccessService = gatedAccessService;
    }

    @Scheduled(fixedDelayString = "${modelhub.catalog.gated-expiry-interval-ms:60000}")
    public void expire() {
        gatedAccessService.expireOverdue();
    }
}
