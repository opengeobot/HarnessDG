/*
 * 功能: 资产 Controller Web 切片测试——校验统一响应包装、创建状态码与检索映射。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.shared.api.CursorPage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AssetController} Web 切片测试。
 */
@WebMvcTest({AssetController.class, AssetCatalogController.class})
@Import({AssetController.class, AssetCatalogController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class})
class AssetControllerTest {

    /** 启动类位于 com.aihub.bootstrap，为切片测试提供本地配置锚点。 */
    @Configuration
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetApplicationService assetService;

    private AssetView modelView() {
        Asset asset = Asset.create("ast_demo", AssetType.MODEL, "nlp", "qwen-domain-7b",
                "领域问答模型", "描述", Visibility.INTERNAL, List.of("team-nlp"),
                List.of("text-generation"), "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", null), null, "usr_01");
        asset.attachRepository(new AssetRepositoryRef("nlp/qwen-domain-7b", null, null));
        return AssetView.from(asset);
    }

    @Test
    void createAssetReturnsCreatedWithUnifiedResponse() throws Exception {
        given(assetService.createAsset(any())).willReturn(modelView());

        String body = """
                {"type":"MODEL","namespace":"nlp","name":"qwen-domain-7b","displayName":"领域问答模型",
                 "visibility":"INTERNAL","owners":["team-nlp"],"tags":["text-generation"],
                 "license":"Apache-2.0","model":{"framework":"pytorch","task":"text-generation"}}
                """;

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PrincipalContextFilter.REQUEST_ID_HEADER, "req_create_1")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("req_create_1"))
                .andExpect(jsonPath("$.data.assetId").value("ast_demo"))
                .andExpect(jsonPath("$.data.type").value("MODEL"))
                .andExpect(jsonPath("$.data.model.framework").value("pytorch"))
                .andExpect(jsonPath("$.data.repository.fullName").value("nlp/qwen-domain-7b"));
    }

    @Test
    void searchModelsReturnsCursorPage() throws Exception {
        AssetSummaryView summary = new AssetSummaryView("ast_demo", AssetType.MODEL, "nlp",
                "qwen-domain-7b", "领域问答模型", "描述", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of("team-nlp"), List.of("text-generation"), "Apache-2.0",
                "pytorch", "text-generation", null, null, Instant.now());
        given(assetService.searchAssets(any())).willReturn(new CursorPage<>(List.of(summary), null, false));

        mockMvc.perform(get("/api/v1/models").param("keyword", "qwen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].assetId").value("ast_demo"))
                .andExpect(jsonPath("$.data.items[0].framework").value("pytorch"))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }
}
