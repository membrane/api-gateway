package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSessionIdExtractorTest {

    static @NotNull Exchange getExchange(Request req) {
        return new Exchange(null) {{
            setRequest(req);
        }};
    }

    static @NotNull Exchange getExchange(Response res) {
        return new Exchange(null) {{
            setResponse(res);
        }};
    }
}
