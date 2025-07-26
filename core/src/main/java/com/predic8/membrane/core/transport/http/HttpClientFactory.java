/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.TimerManager;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.WeakHashMap;

import static java.util.Objects.hash;

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
    private final WeakHashMap<Config, HttpClient> clients = new WeakHashMap<>();

    public HttpClientFactory(@Nullable TimerManager timerManager) {
        this.timerManager = timerManager;
    }

    public synchronized HttpClient createClient(@Nullable HttpClientConfiguration hcc) {
        Config config = new Config(hcc, timerManager);
        HttpClient hc = clients.get(config);
        if (hc != null)
            return hc;

        hc = new HttpClient(hcc, timerManager);
        clients.put(config, hc);
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
            return hash(httpClientConfiguration, timerManager);
        }
    }
}