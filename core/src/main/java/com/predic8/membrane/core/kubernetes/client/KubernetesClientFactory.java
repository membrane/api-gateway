package com.predic8.membrane.core.kubernetes.client;

import com.predic8.membrane.core.transport.http.HttpClientFactory;

import java.util.WeakHashMap;

/**
 * Sharing the KubernetesClient instances has two benefits:
 * <ul>
 * <li>The KubernetesClient only uses one HttpClient and thereby shares the timer (connection closer) and connection pool.</li>
 * <li>The KubernetesClient only downloads the schema once (speeding up initialization).</li>
 * </ul>
 *
 *
 * Note: "baseUrl" is the KubernetesClient's only supported configuration. (KubernetesClientBuilder supports more, which
 * would need to be implemented here.)
 */
public class KubernetesClientFactory {
    private WeakHashMap<String, KubernetesClient> clients;
    private final HttpClientFactory httpClientFactory;

    public KubernetesClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    public synchronized KubernetesClient createClient(String baseUrl) {
        if (clients == null)
            clients = new WeakHashMap<>();
        KubernetesClient client = clients.get(baseUrl);
        if (client == null)
            try {
                KubernetesClientBuilder builder = KubernetesClientBuilder.auto()
                        .httpClientFactory(httpClientFactory);
                if (baseUrl != null)
                    builder.baseURL(baseUrl);
                client = builder.build();
                clients.put(baseUrl, client);
            } catch (KubernetesClientBuilder.ParsingException e) {
                throw new RuntimeException(e);
            }
        return client;
    }
}
