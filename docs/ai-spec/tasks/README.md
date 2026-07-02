# 正式 AI 实施任务

本目录只保存由产品/架构责任人批准的 `harnessdg.task/v1` Task Card。

- DRAFT Task 可以由 AI 协助起草，但不能授权产品代码修改；
- READY Task 必须设置 `implementationAuthorized: true`、真实 base Commit、阶段出口证据和窄
  `allowedPaths`；
- 每条 Acceptance 必须在 `scenarioEvidencePlan` 中绑定证据等级和预期产物；精确验证命令写入
  `requiredValidationCommands`；
- `preExistingDirtyPaths` 只能使用 `repository-path|SHA-256`，结束检查只忽略内容摘要仍与批准时
  一致的文件，不能把目录当范围豁免；
- AI 不得在同一实施会话中自行填写 `approvedBy/approvedAt` 或把 Task 从 DRAFT 改为 READY；
- 实施前必须运行：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath <task-card>
```

交接前增加 `-CheckChangedPaths`；只有验证责任方已接受 Evidence 且 `-CheckCompletion` 退出 0 时，
才允许使用“完成”，否则标记为 `IMPLEMENTED_UNVERIFIED`。

`.trae/specs`、IDE Plan、聊天记录和历史 checklist 都不能替代本目录中的正式 Task Card。
