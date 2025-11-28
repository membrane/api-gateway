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

package com.predic8.membrane.integration.withoutinternet.interceptor.oauth2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.BufferedJsonGenerator;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerJwtTokenGenerator;
import com.predic8.membrane.core.proxies.NullProxy;

import java.io.IOException;

public class OAuth2TestUtil {

    public static String sessionId = null;

    static String getMockClaims() throws IOException {
        try (var bufferedJsonGenerator = new BufferedJsonGenerator()) {
            var gen = bufferedJsonGenerator.getJsonGenerator();
            gen.writeStartObject();

            gen.writeName("userinfo");
            gen.writeStartObject();
            gen.writeName("email");
            gen.writeNull();
            gen.writeEndObject();

            gen.writeName("id_token");
            gen.writeStartObject();
            gen.writeName("sub");
            gen.writeNull();
            gen.writeName("email");
            gen.writeNull();
            gen.writeEndObject();

            gen.writeEndObject();
            return bufferedJsonGenerator.getJson();
        }
    }

    public static void makeExchangeValid(Exchange exc) {
        exc.setOriginalRequestUri(exc.getRequest().getUri());
        exc.setProxy(new NullProxy());
    }

    public static void useJWTForAccessTokensAndRefreshTokens(OAuth2AuthorizationServerInterceptor oasi) {
        BearerJwtTokenGenerator tokenGenerator = new BearerJwtTokenGenerator();
        tokenGenerator.setWarningGeneratedKey(false);
        oasi.setTokenGenerator(tokenGenerator);
        OAuth2AuthorizationServerInterceptor.RefreshTokenConfig refreshTokenConfig = new OAuth2AuthorizationServerInterceptor.RefreshTokenConfig();
        BearerJwtTokenGenerator refreshTokenGenerator = new BearerJwtTokenGenerator();
        refreshTokenGenerator.setWarningGeneratedKey(false);
        refreshTokenConfig.setTokenGenerator(refreshTokenGenerator);
        oasi.setRefreshTokenConfig(refreshTokenConfig);
    }
}
