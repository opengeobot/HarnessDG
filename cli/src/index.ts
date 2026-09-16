#!/usr/bin/env node
// AIHub CLI 入口文件
// 作者: AxeXie
// 创建时间: 2026-07-30

import { Command } from 'commander';
import { createAuthCommand } from './auth/auth.command';
import { createDatasetSearchCommand } from './commands/dataset.search';
import { createDatasetStatusCommand } from './commands/dataset.status';
import { createDatasetCreateCommand } from './commands/dataset.create';
import { createDatasetPullCommand } from './commands/dataset.pull';
import { createDatasetPushCommand } from './commands/dataset.push';

const program = new Command();

program
  .name('aih')
  .description('AIHub Platform Dataset CLI - 内部 AI 资产管理平台命令行工具')
  .version('0.1.0')
  .option('--json', 'Output in JSON format for machine-readable parsing');

// Auth 子命令组
program.addCommand(createAuthCommand());

// Dataset 子命令组
const datasetCmd = new Command('dataset');
datasetCmd.description('Manage datasets and assets');
datasetCmd.addCommand(createDatasetSearchCommand());
datasetCmd.addCommand(createDatasetStatusCommand());
datasetCmd.addCommand(createDatasetCreateCommand());
datasetCmd.addCommand(createDatasetPullCommand());
datasetCmd.addCommand(createDatasetPushCommand());
program.addCommand(datasetCmd);

program.parse(process.argv);