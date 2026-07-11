package com.example.bigevent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置
 * <p>
 * 注意：elasticsearch-java 版本需与 ES 服务端版本一致。
 * 本地开发默认无认证，生产环境建议开启 xpack.security 并配置用户名密码。
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;
/**
 * 创建 ElasticsearchClient 实例
 * 后续你的文档上传、Chunk 存储、向量检索、混合检索等功能，
 * 都会复用这个 ElasticsearchClient 来操作 Elasticsearch。
 */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, scheme));

        if (hasCredentials()) {
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(username, password)
                );
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            });
        }

        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    private boolean hasCredentials() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
