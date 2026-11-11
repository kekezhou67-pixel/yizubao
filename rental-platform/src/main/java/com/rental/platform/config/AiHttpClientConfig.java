package com.rental.platform.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * AI HTTP 客户端连接配置（AI 改造 · 阶段3 实测修复）
 *
 * 问题：调用 DeepSeek 高频出现
 *   reactor.netty.http.client.PrematureCloseException: Connection prematurely closed BEFORE response
 *
 * 排查过程（实测记录）：
 * 1. curl 直连 DeepSeek 连续 3 次全部成功（每次新建连接）
 * 2. Java reactor-netty 复用 keep-alive 连接时必挂
 * 3. 本机存在代理软件（127.0.0.1:7897，Clash 系）——代理/TUN 层会掐断
 *    空闲的 keep-alive 长连接，但短连接不受影响
 *
 * 修复：ConnectionProvider.newConnection() —— 每次请求新建连接，跟 curl 行为一致，
 * 彻底规避死连接复用。TLS 握手约增加 300ms，对本项目完全可接受。
 * 配合 Controller 层的重试 + 旧链路降级，可靠性三重保障。
 *
 * 同时作用于 RestClient（同步 call）和 WebClient（流式 stream）。
 */
@Configuration
public class AiHttpClientConfig {

    private HttpClient aiHttpClient() {
        return HttpClient.create(ConnectionProvider.newConnection())   // ★ 不复用连接
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(90));   // LLM 生成慢，放宽到 90s
    }

    /** 同步调用（.call()）走的 RestClient */
    @Bean
    public RestClientCustomizer aiRestClientCustomizer() {
        ReactorClientHttpRequestFactory factory =
                new ReactorClientHttpRequestFactory(aiHttpClient());
        return builder -> builder.requestFactory(factory);
    }

    /** 流式调用（.stream()）走的 WebClient */
    @Bean
    public WebClientCustomizer aiWebClientCustomizer() {
        ReactorClientHttpConnector connector =
                new ReactorClientHttpConnector(aiHttpClient());
        return builder -> builder.clientConnector(connector);
    }
}
