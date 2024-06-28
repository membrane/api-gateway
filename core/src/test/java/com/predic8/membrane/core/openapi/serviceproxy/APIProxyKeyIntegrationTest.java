//package com.predic8.membrane.core.openapi.serviceproxy;
//
//import com.predic8.membrane.core.*;
//import com.predic8.membrane.core.exchange.Exchange;
//import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
//import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
//import com.predic8.membrane.core.rules.*;
//import com.predic8.membrane.core.transport.http.*;
//import org.jetbrains.annotations.NotNull;
//import org.junit.jupiter.api.*;
//
//import java.io.*;
//import java.net.InetAddress;
//
//public class APIProxyKeyIntegrationTest {
////    @Test
////    void getRuleByKeyTest() throws Exception {
////        Rule api = new APIProxy() {{
////            setHost("localhost");
////            setIp("127.0.0.1");
////            setPort(8050);
////        }};
////        api.getInterceptors().add(new ReturnInterceptor());
////        var r = new HttpRouter();
////        r.getRuleManager().addProxyAndOpenPortIfNew(api);
////        r.setExchangeStore(new LimitedMemoryExchangeStore());
////        r.init();
////        APIProxyKey.getRuleByKey(buildMockExchange(r), (AbstractRuleKey) api.getKey());
////    }
//
//    @Test
//    void complexMatchExpressionTest() throws IOException {
//
//        Router router = new Router();
//        Exchange exc = new Exchange(router.get);
//
//
//
//
//        APIProxy api = new APIProxy();
//        api.setTest("false");
//        RuleKey key = api.getKey();
//
//        Assertions.assertFalse(key.complexMatch(exc));
//    }
//
//    private static @NotNull Exchange buildMockExchange(HttpRouter r) throws Exception {
//        return new Exchange(new AbstractHttpHandler(new MockHttpTransport() {{
//            init(r);
//        }}) {
//            @Override
//            public void shutdownInput() {
//            }
//            @Override
//            public InetAddress getLocalAddress() {
//                return null;
//            }
//            @Override
//            public int getLocalPort() {
//                return 0;
//            }
//        });
//    }
//}
