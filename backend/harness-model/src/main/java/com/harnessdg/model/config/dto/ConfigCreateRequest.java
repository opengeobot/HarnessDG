/**
 * 功能：创建系统配置请求
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ConfigCreateRequest {

    @NotBlank
    private String configKey;

    private String configValue;

    /** string / number / boolean / json */
    private String valueType = "string";

    @NotBlank
    private String category;

    private Map<String, String> description;

    private Boolean isEncrypted = false;

    private Boolean isReadonly = false;

    /** all / dev / test / prod */
    private String environment = "all";

    private String comment;
}
