/*
 * 功能: 基于 Gitea REST API 的仓库存在性查询适配器。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gitea REST 仓库存在性查询。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "true")
public class RestGiteaRepositoryExistencePort implements GiteaRepositoryExistencePort {

    private static final Logger LOG = LoggerFactory.getLogger(RestGiteaRepositoryExistencePort.class);

    private final RestClient restClient;

    public RestGiteaRepositoryExistencePort(RestClient.Builder restClientBuilder, GiteaProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .build();
    }

    @Override
    public boolean repositoryExists(String namespace, String name) {
        try {
            restClient.get()
                    .uri("/api/v1/repos/{namespace}/{name}", namespace, name)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        } catch (RestClientException ex) {
            LOG.warn("failed to query gitea repository {}/{}: {}", namespace, name, ex.getMessage());
            return false;
        }
    }
}
