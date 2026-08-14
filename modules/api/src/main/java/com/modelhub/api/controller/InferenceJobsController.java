package com.modelhub.api.controller;

import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inference jobs placeholder (05 sec 3): returns 403 FEATURE_DISABLED.
 */
@RestController
@RequestMapping("/api/v1/inference-jobs")
public class InferenceJobsController {

    @RequestMapping("/**")
    public Object handle() {
        throw new ApiException(ErrorCode.FORBIDDEN, "FEATURE_DISABLED: inference jobs not yet available");
    }
}