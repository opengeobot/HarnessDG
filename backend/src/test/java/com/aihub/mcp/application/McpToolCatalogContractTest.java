/*
 * 功能: 校验 contracts/mcp/tools.yaml 与 McpToolCatalog 工具集合一致。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * MCP Tool Catalog 与契约 YAML 同步断言。
 */
class McpToolCatalogContractTest {

    private static final Pattern TOOL_NAME = Pattern.compile("^\\s+- name:\\s+(\\S+)\\s*$");
    private static final Pattern WRITE_FLAG = Pattern.compile("^\\s+write:\\s+(true|false)\\s*$");

    @Test
    void catalogToolNamesShouldMatchToolsYaml() throws IOException {
        Path yaml = resolveToolsYaml();
        Set<String> yamlTools = parseToolNames(yaml);
        Set<String> catalogTools = catalog().listTools().stream()
                .map(McpToolCatalog.ToolDefinition::name)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(catalogTools).isEqualTo(yamlTools);
    }

    @Test
    void catalogWriteFlagsShouldMatchToolsYaml() throws IOException {
        Path yaml = resolveToolsYaml();
        Map<String, Boolean> yamlWriteFlags = parseWriteFlags(yaml);
        Map<String, Boolean> catalogWriteFlags = new HashMap<>();
        catalog().listTools().forEach(tool -> catalogWriteFlags.put(tool.name(), tool.write()));

        assertThat(catalogWriteFlags).isEqualTo(yamlWriteFlags);
    }

    @Test
    void visibleToolsShouldHideWriteToolsWhenDisabled() {
        McpToolCatalog disabled = catalog(false);
        List<String> visible = disabled.listVisibleTools().stream()
                .map(McpToolCatalog.ToolDefinition::name)
                .toList();

        assertThat(visible).containsExactlyInAnyOrder(
                "asset_search", "asset_get", "asset_list_versions",
                "asset_get_version", "asset_request_download");
        assertThat(visible).doesNotContain(
                "asset_create_draft", "asset_publish_version", "asset_delete");
    }

    private static McpToolCatalog catalog() {
        return catalog(true);
    }

    private static McpToolCatalog catalog(boolean writeEnabled) {
        return new McpToolCatalog(
                org.mockito.Mockito.mock(com.aihub.asset.application.AssetApplicationService.class),
                org.mockito.Mockito.mock(com.aihub.version.application.VersionApplicationService.class),
                org.mockito.Mockito.mock(com.aihub.version.domain.VersionRepository.class),
                org.mockito.Mockito.mock(com.aihub.transfer.application.DownloadApplicationService.class),
                org.mockito.Mockito.mock(com.aihub.transfer.application.UploadApplicationService.class),
                org.mockito.Mockito.mock(com.aihub.version.application.PublishApplicationService.class),
                writeEnabled);
    }

    private static Path resolveToolsYaml() {
        Path fromModule = Path.of("..", "contracts", "mcp", "tools.yaml");
        if (Files.exists(fromModule)) {
            return fromModule.normalize();
        }
        Path fromRoot = Path.of("contracts", "mcp", "tools.yaml");
        if (Files.exists(fromRoot)) {
            return fromRoot.normalize();
        }
        throw new IllegalStateException("contracts/mcp/tools.yaml not found");
    }

    private static Set<String> parseToolNames(Path yaml) throws IOException {
        Set<String> names = new HashSet<>();
        for (String line : Files.readAllLines(yaml)) {
            Matcher matcher = TOOL_NAME.matcher(line);
            if (matcher.matches()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static Map<String, Boolean> parseWriteFlags(Path yaml) throws IOException {
        Map<String, Boolean> flags = new HashMap<>();
        String currentTool = null;
        for (String line : Files.readAllLines(yaml)) {
            Matcher nameMatcher = TOOL_NAME.matcher(line);
            if (nameMatcher.matches()) {
                currentTool = nameMatcher.group(1);
                continue;
            }
            if (currentTool != null) {
                Matcher writeMatcher = WRITE_FLAG.matcher(line);
                if (writeMatcher.matches()) {
                    flags.put(currentTool, Boolean.parseBoolean(writeMatcher.group(1)));
                    currentTool = null;
                }
            }
        }
        return flags;
    }
}
