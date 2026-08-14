package com.modelhub.api.controller;

import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent jobs placeholder (05 sec 3): returns 403 FEATURE_DISABLED.
 */
@RestController
@RequestMapping("/api/v1/agent-jobs")
public class AgentJobsController {

    @RequestMapping("/**")
    public Object handle() {
        throw new ApiException(ErrorCode.FORBIDDEN, "FEATURE_DISABLED: agent jobs not yet available");
    }
}