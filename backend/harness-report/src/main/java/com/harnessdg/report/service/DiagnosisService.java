/**
 * 功能：异常诊断服务
 * 时间：2026-05-12
 * 作者：AxeXie
 *
 * 任务失败时自动触发诊断分析，返回根因分类和修复建议
 */
package com.harnessdg.report.service;

import com.harnessdg.agent.service.AgentGatewayService;
import com.harnessdg.model.report.dto.DiagnosisResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final AgentGatewayService agentGateway;

    /**
     * 对失败的任务进行诊断分析
     *
     * @param taskId 任务 ID
     * @param taskType 任务类型
     * @param errorMessage 错误信息
     * @return 诊断结果
     */
    public DiagnosisResultDTO diagnose(Long taskId, String taskType, String errorMessage) {
        log.info("Diagnosing failed task: taskId={}, type={}", taskId, taskType);

        DiagnosisResultDTO result = new DiagnosisResultDTO();
        result.setTaskId(taskId);
        result.setTaskType(taskType);
        result.setErrorMessage(errorMessage);
        result.setDiagnosedAt(OffsetDateTime.now());

        try {
            // 使用 AI Agent 进行根因分析
            String prompt = buildDiagnosisPrompt(taskType, errorMessage);
            Map<String, Object> agentResponse = agentGateway.chatBlocking(
                    "diagnosis-" + taskId,
                    prompt,
                    Map.of("task_type", taskType, "error", errorMessage)
            );

            if (agentResponse != null && agentResponse.containsKey("reply")) {
                String analysis = (String) agentResponse.get("reply");
                result.setRootCauseCategory(extractCategory(analysis, errorMessage));
                result.setRootCauseDescription(analysis);
                result.setFixSuggestions(extractSuggestions(analysis, errorMessage));
            } else {
                result.setRootCauseCategory("unknown");
                result.setRootCauseDescription("无法确定根因");
                result.setFixSuggestions(List.of("请检查日志获取更多信息"));
            }
        } catch (Exception e) {
            log.error("Failed to diagnose task: taskId={}", taskId, e);
            result.setRootCauseCategory("diagnosis_failed");
            result.setRootCauseDescription("诊断服务异常: " + e.getMessage());
            result.setFixSuggestions(List.of("请联系管理员"));
        }

        return result;
    }

    /**
     * 构建诊断提示
     */
    private String buildDiagnosisPrompt(String taskType, String errorMessage) {
        return String.format("""
                请分析以下数据平台任务失败的根因，并给出修复建议：

                任务类型: %s
                错误信息: %s

                请从以下分类中选择根因类别：
                - data_quality: 数据质量问题（空值、格式错误、超范围等）
                - connection: 连接问题（网络超时、认证失败、服务不可用等）
                - configuration: 配置问题（参数错误、权限不足等）
                - resource: 资源问题（内存不足、磁盘满、并发过高）
                - code: 代码/逻辑问题（SQL 语法错误、转换逻辑错误等）
                - unknown: 无法确定

                请返回：1)根因分类 2)详细说明 3)修复建议列表
                """, taskType, errorMessage);
    }

    /**
     * 从分析结果提取类别
     */
    private String extractCategory(String analysis, String errorMessage) {
        String lower = (analysis + " " + errorMessage).toLowerCase();
        if (lower.contains("timeout") || lower.contains("connect") || lower.contains("network")) {
            return "connection";
        } else if (lower.contains("null") || lower.contains("format") || lower.contains("invalid")) {
            return "data_quality";
        } else if (lower.contains("permission") || lower.contains("auth") || lower.contains("config")) {
            return "configuration";
        } else if (lower.contains("memory") || lower.contains("disk") || lower.contains("resource")) {
            return "resource";
        } else if (lower.contains("syntax") || lower.contains("sql") || lower.contains("logic")) {
            return "code";
        }
        return "unknown";
    }

    /**
     * 从分析结果提取建议
     */
    private List<String> extractSuggestions(String analysis, String errorMessage) {
        // 基于错误类型返回预设建议
        String lower = errorMessage.toLowerCase();
        if (lower.contains("timeout")) {
            return List.of(
                    "检查网络连接是否稳定",
                    "增加任务超时配置",
                    "确认目标服务是否正常运行"
            );
        } else if (lower.contains("authentication") || lower.contains("permission")) {
            return List.of(
                    "检查数据库用户名密码是否正确",
                    "确认账号是否有足够权限",
                    "检查账号是否过期或被锁定"
            );
        } else if (lower.contains("null") || lower.contains("format")) {
            return List.of(
                    "检查源数据是否有异常空值",
                    "确认数据格式是否符合预期",
                    "添加数据预处理清洗步骤"
            );
        }
        return List.of("检查任务日志获取详细错误信息", "联系管理员协助排查");
    }
}
