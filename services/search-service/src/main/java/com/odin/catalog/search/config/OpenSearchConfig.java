package com.odin.catalog.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.uris:http://localhost:9200}")
    private String opensearchUri;

    @Bean
    public OpenSearchClient openSearchClient(ObjectMapper objectMapper) {
        URI uri = URI.create(opensearchUri);
        RestClient restClient = RestClient
            .builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
            .build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
        return new OpenSearchClient(transport);
    }
}
