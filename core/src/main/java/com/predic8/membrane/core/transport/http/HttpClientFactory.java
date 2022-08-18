package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.kubernetes.client.KubernetesClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.TimerManager;

import javax.annotation.Nullable;
import javax.validation.constraints.Null;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Sharing the HttpClient instances has two benefits:
 * <ul>
 * <li>The HttpClient only uses one timer (connection closer).</li>
 * <li>The HttpClient only uses one connection pool.</li>
 * </ul>
 */
public class HttpClientFactory {
    @Nullable
    private final TimerManager timerManager;
    private WeakHashMap<Config, HttpClient> clients;

    public HttpClientFactory(@Nullable TimerManager timerManager) {
        this.timerManager = timerManager;
    }

    public synchronized HttpClient createClient(@Nullable HttpClientConfiguration httpClientConfiguration) {
        if (clients == null)
            clients = new WeakHashMap<>();
        Config config = new Config(httpClientConfiguration, timerManager);
        HttpClient hc = clients.get(config);
        if (hc == null) {
            hc = new HttpClient(httpClientConfiguration, timerManager);
            clients.put(config, hc);
        }
        return hc;
    }

    private static class Config {
        final HttpClientConfiguration httpClientConfiguration;
        final TimerManager timerManager;

        public Config(@Nullable HttpClientConfiguration httpClientConfiguration, @Nullable TimerManager timerManager) {
            this.httpClientConfiguration = httpClientConfiguration;
            this.timerManager = timerManager;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Config config = (Config) o;
            return Objects.equals(httpClientConfiguration, config.httpClientConfiguration)
                    && Objects.equals(timerManager, config.timerManager);
        }

        @Override
        public int hashCode() {
            return Objects.hash(httpClientConfiguration,
                    timerManager);
        }
    }
}
