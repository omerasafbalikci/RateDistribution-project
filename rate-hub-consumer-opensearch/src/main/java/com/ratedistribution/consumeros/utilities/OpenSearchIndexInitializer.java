package com.ratedistribution.consumeros.utilities;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OpenSearchIndexInitializer {
    private final OpenSearchClient client;

    @Value("${opensearch.index-name}")
    private String indexName;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexExists() {
        try {
            boolean exists = client.indices()
                    .exists(e -> e.index(indexName))
                    .value();

            if (!exists) {
                client.indices()
                        .create(c -> c.index(indexName));
            } else {
            }
        } catch (IOException e) {
        }
    }
}
