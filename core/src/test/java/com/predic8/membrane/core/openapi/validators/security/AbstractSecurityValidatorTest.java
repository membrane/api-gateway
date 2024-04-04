/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.validators.security;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.security.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;

public abstract class AbstractSecurityValidatorTest {

    @NotNull
    Exchange getExchange(String path, SecurityScheme scheme) throws URISyntaxException {
        return getExchange("GET", path, scheme);
    }

    @NotNull
    Exchange getExchange(String method, String path, SecurityScheme scheme) throws URISyntaxException {
        Exchange exc = new Request.Builder().method(method).url(new URIFactory(),path).buildExchange();
        exc.setOriginalRequestUri(path);
        if (scheme!=null)
            exc.setProperty(SECURITY_SCHEMES, List.of(scheme));
        return exc;
    }

    static Router getRouter() {
        Router router = new Router();
        router.setUriFactory(new URIFactory());
        return router;
    }
}
