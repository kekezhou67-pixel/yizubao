package com.rental.platform.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置（AI 改造 · 阶段5）
 *
 * 依据讲义04 项目4：Java 侧用官方 elasticsearch-java 客户端（8.13.1）做 BM25 检索，
 * ⚠️ 客户端版本必须与服务端一致（8.13.x），9.x 客户端连 8.x 服务端会报 BadRequestError。
 *
 * dev:  http://localhost:9200（本机 ES）
 * prod: http://elasticsearch:9200（docker-compose 服务名）
 */
@Configuration
public class EsConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(
            @Value("${ai.es.base-url:http://localhost:9200}") String esUrl) {
        RestClient restClient = RestClient.builder(HttpHost.create(esUrl)).build();
        ElasticsearchTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
