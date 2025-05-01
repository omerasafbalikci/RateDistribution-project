package com.ratedistribution.consumeros.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {
    @Value("${opensearch.host}")
    private String host;
    @Value("${opensearch.port}")
    private int port;
    @Value("${opensearch.scheme}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        // 1) HTTP istemcisi
        RestClient restClient = RestClient.builder(
                new HttpHost(host, port, scheme)
        ).build();

        // 2) Transport (JSON-P mapper: Jackson kullanan)
        RestClientTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper()
        );

        // 3) OpenSearchClient nesnesi
        return new OpenSearchClient(transport);
    }
}
