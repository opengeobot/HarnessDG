/**
 * 功能：SeaTunnel Pipeline 模板引擎
 * 时间：2026-05-12
 * 作者：AxeXie
 *
 * 根据数据源配置生成 SeaTunnel .conf 文件
 * 支持 JDBC Source/Sink、转换配置、并行度设置
 */
package com.harnessdg.seatunnel.template;

import com.harnessdg.model.datasource.dto.DataSourceDTO;
import com.harnessdg.model.datasource.dto.IngestionTaskDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SeaTunnel Pipeline 配置模板引擎
 * 根据数据源和任务配置动态生成 SeaTunnel 配置文件
 */
@Slf4j
@Component
public class PipelineTemplateEngine {

    /**
     * 根据任务配置生成 SeaTunnel Pipeline 配置
     *
     * @param source 数据源配置
     * @param task 接入任务配置
     * @return SeaTunnel .conf 文件内容
     */
    public String generatePipelineConfig(DataSourceDTO source, IngestionTaskDTO task) {
        log.info("Generating SeaTunnel pipeline config for task: {}", task.getTaskName());

        StringBuilder conf = new StringBuilder();

        // env 部分
        conf.append("env {\n");
        conf.append(String.format("  job.name = \"%s\"\n", task.getTaskName()));
        conf.append("  parallelism = 1\n");
        conf.append("  checkpoint.interval = 10000\n");
        conf.append("}\n\n");

        // source 部分
        conf.append(buildSource(source, task));

        // sink 部分
        conf.append(buildSink(task));

        return conf.toString();
    }

    /**
     * 构建 Source 配置
     */
    @SuppressWarnings("unchecked")
    private String buildSource(DataSourceDTO source, IngestionTaskDTO task) {
        Map<String, Object> connConfig = source.getConnectionConfig();
        if (connConfig == null) {
            throw new IllegalArgumentException("Data source connection config is empty");
        }

        String host = (String) connConfig.get("host");
        int port = connConfig.get("port") != null ? (int) connConfig.get("port") : 3306;
        String database = (String) connConfig.get("database");
        String username = (String) connConfig.get("username");
        String password = (String) connConfig.get("password");
        String table = task.getSourceTable();

        return switch (source.getSourceType().toLowerCase()) {
            case "mysql" -> String.format("""
                    source {
                      Jdbc {
                        url = "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC"
                        driver = "com.mysql.cj.jdbc.Driver"
                        user = "%s"
                        password = "%s"
                        query = "SELECT * FROM %s"
                      }
                    }

                    """, host, port, database, username, password, table);
            case "postgresql" -> String.format("""
                    source {
                      Jdbc {
                        url = "jdbc:postgresql://%s:%d/%s"
                        driver = "org.postgresql.Driver"
                        user = "%s"
                        password = "%s"
                        query = "SELECT * FROM %s"
                      }
                    }

                    """, host, port, database, username, password, table);
            default -> throw new IllegalArgumentException("Unsupported source type: " + source.getSourceType());
        };
    }

    /**
     * 构建 Sink 配置（目标为 HarnessDG 数仓 PostgreSQL）
     */
    private String buildSink(IngestionTaskDTO task) {
        // 默认目标表使用源表名加后缀
        String targetTable = task.getSourceTable() + "_stg";

        return String.format("""
                sink {
                  Jdbc {
                    url = "jdbc:postgresql://postgres:5432/harnessdg"
                    driver = "org.postgresql.Driver"
                    user = "harness"
                    password = "harness_dev"
                    table = "%s"
                    primary_keys = ["id"]
                  }
                }
                """, targetTable);
    }
}
