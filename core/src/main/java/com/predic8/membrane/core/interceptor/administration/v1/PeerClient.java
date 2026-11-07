/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.interceptor.administration.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.http.Header.*;

/**
 * Calls the admin API of peer instances. Thread safe.
 */
class PeerClient {

    /**
     * Marks a request sent by an aggregating console. The receiver answers it with its own data only, so that consoles
     * that list each other as peers do not loop.
     */
    static final String FORWARDED = "X-Membrane-Admin-Forwarded";

    static final String API_KEY_HEADER = "X-Api-Key";

    /**
     * Response headers passed on to the browser when a request is forwarded to a peer.
     */
    private static final List<String> PASSED_HEADERS = List.of(CONTENT_TYPE, CONTENT_DISPOSITION, CONTENT_ENCODING,
            X_CONTENT_TYPE_OPTIONS, CONTENT_SECURITY_POLICY, ALLOW);

    record PeerResponse(int status, Map<String, String> headers, byte[] body) {
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * @param pathAndQuery path below the peer's base URL, starting with {@code /admin/api/v1}
     */
    CompletableFuture<PeerResponse> send(AdminConsolePeer peer, String method, String pathAndQuery, String contentType,
                                         byte[] body, Duration timeout) {
        final HttpRequest request;
        try {
            final var builder = HttpRequest.newBuilder(URI.create(baseUrl(peer) + pathAndQuery))
                    .timeout(timeout)
                    .header(FORWARDED, "1")
                    .method(method, body == null || body.length == 0
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(body));
            if (peer.getApiKey() != null)
                builder.header(API_KEY_HEADER, peer.getApiKey());
            if (contentType != null)
                builder.header(CONTENT_TYPE, contentType);
            request = builder.build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(PeerClient::toPeerResponse);
    }

    private static PeerResponse toPeerResponse(HttpResponse<byte[]> response) {
        final var headers = new LinkedHashMap<String, String>();
        for (final var name : PASSED_HEADERS)
            response.headers().firstValue(name).ifPresent(v -> headers.put(name, v));
        return new PeerResponse(response.statusCode(), headers, response.body());
    }

    static String baseUrl(AdminConsolePeer peer) {
        final var url = peer.getUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
