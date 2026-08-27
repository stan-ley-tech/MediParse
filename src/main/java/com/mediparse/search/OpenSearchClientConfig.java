package com.mediparse.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediparse.config.OpenSearchProperties;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;

/**
 * The bundled OpenSearch container runs with its security plugin enabled but
 * only a self-signed demo certificate, so the client trusts any certificate
 * for local/dev use. A real deployment should point this at a proper CA
 * chain instead of the permissive TrustStrategy below.
 */
@Configuration
public class OpenSearchClientConfig {

    private final OpenSearchProperties properties;

    public OpenSearchClientConfig(OpenSearchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OpenSearchClient openSearchClient() throws Exception {
        HttpHost host = new HttpHost(properties.scheme(), properties.host(), properties.port());
        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(host);

        if (hasCredentials()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(host),
                    new UsernamePasswordCredentials(properties.username(), properties.password().toCharArray()));

            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                if ("https".equalsIgnoreCase(properties.scheme())) {
                    httpClientBuilder.setConnectionManager(insecureConnectionManager());
                }
                return httpClientBuilder;
            });
        }

        // The client's internal Jackson mapper is independent of Spring's own
        // ObjectMapper and doesn't register JSR-310 by default, so java.time
        // types (e.g. IndexedDocument.createdAt) fail to serialize without this.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        builder.setMapper(new JacksonJsonpMapper(objectMapper));

        OpenSearchTransport transport = builder.build();
        return new OpenSearchClient(transport);
    }

    private boolean hasCredentials() {
        return properties.username() != null && !properties.username().isBlank();
    }

    private org.apache.hc.client5.http.nio.AsyncClientConnectionManager insecureConnectionManager() {
        try {
            TrustStrategy trustAll = (chain, authType) -> true;
            SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(null, trustAll).build();
            TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();
            return PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(tlsStrategy)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure OpenSearch TLS connection manager", e);
        }
    }
}
