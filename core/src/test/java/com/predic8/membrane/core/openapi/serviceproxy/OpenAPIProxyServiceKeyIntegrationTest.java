package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.rules.AbstractRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.MockHttpTransport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

public class OpenAPIProxyServiceKeyIntegrationTest {
    @Test
    void getRuleByKeyTest() throws Exception {
        Rule api = new APIProxy() {{
            setHost("localhost");
            setIp("127.0.0.1");
            setPort(8050);
        }};
        api.getInterceptors().add(new ReturnInterceptor());
        var r = new HttpRouter();
        r.getRuleManager().addProxyAndOpenPortIfNew(api);
        r.setExchangeStore(new LimitedMemoryExchangeStore());
        r.init();
        OpenAPIProxyServiceKey.getRuleByKey(buildMockExchange(r), (AbstractRuleKey) api.getKey());
    }

    @Test
    void complexMatchExpressionTest() {

    }

    private static @NotNull Exchange buildMockExchange(HttpRouter r) throws Exception {
        return new Exchange(new AbstractHttpHandler(new MockHttpTransport() {{
            init(r);
        }}) {
            @Override
            public void shutdownInput() {
            }
            @Override
            public InetAddress getLocalAddress() {
                return null;
            }
            @Override
            public int getLocalPort() {
                return 0;
            }
        });
    }
}
