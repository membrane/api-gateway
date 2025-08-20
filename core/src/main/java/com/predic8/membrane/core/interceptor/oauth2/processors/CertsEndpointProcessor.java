/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.processors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.prettifier.*;
import org.jetbrains.annotations.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

public class CertsEndpointProcessor extends EndpointProcessor {

    private final JSONPrettifier prettifier = new JSONPrettifier();

    public CertsEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return exc.getRequestURI().startsWith(authServer.getBasePath() + "/oauth2/certs");
    }

    @Override
    public Outcome process(Exchange exc) {
        exc.setResponse(ok().contentType(APPLICATION_JSON_UTF8).body(prettifier.prettify(getJwks().getBytes(UTF_8), UTF_8)).build());
        return RETURN;
    }

    private @NotNull String getJwks() {
        String accessTokenJWKIfAvailable = authServer.getTokenGenerator().getJwkIfAvailable();
        String idTokenJWK = authServer.getJwtGenerator().getJwk();

        return  "{\"keys\": [ " + idTokenJWK +
                      (accessTokenJWKIfAvailable != null ? "," + accessTokenJWKIfAvailable : "") +
                      "]}";
    }
}