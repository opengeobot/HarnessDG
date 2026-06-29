# tiny-model

用于 Compose 端到端验证的最小示例模型仓库（P0 基线占位）。

该目录在后续阶段（P1+）用于演示"创建资产 → 上传 DVC 内容 → Git 提交 → 发布版本 → 下载校验"的端到端用例。当前 P0 阶段仅提供 fixtures 占位文件。

- `asset.yaml`：平台可机器读取的资产清单。
- `tiny-model.bin`：体积极小的占位权重文件，用于 DVC 往返校验。
