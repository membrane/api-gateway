/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.services.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static org.apache.http.params.CoreProtocolPNames.*;
import static org.junit.jupiter.api.Assertions.*;

public class LoadBalancingWithClusterManagerTest {

    private Router lb;
    private DefaultRouter node1;
    private DefaultRouter node2;
    private DefaultRouter node3;

    @AfterEach
    public void tearDown() {
        lb.stop();
        node1.stop();
        node2.stop();
        node3.stop();
    }

    @Test
    void nodesTest() throws Exception {
        node1 = createRouter();
        node2 = createRouter();
        node3 = createRouter();

        DummyWebServiceInterceptor service1 = startNode(node1, 2000);
        DummyWebServiceInterceptor service2 = startNode(node2, 3000);
        DummyWebServiceInterceptor service3 = startNode(node3, 4000);

        startLB();

        sendNotification("up", 2000);
        sendNotification("up", 3000);

        assertEquals(200, post("/getBankwithSession555555.xml"));// goes to service one
        assertEquals(1, service1.getCount());
        assertEquals(0, service2.getCount());

        assertEquals(200, post("/getBankwithSession555555.xml"));// goes to service 1 again
        assertEquals(2, service1.getCount());
        assertEquals(0, service2.getCount());

        assertEquals(200, post("/getBankwithSession444444.xml")); // goes to service 2
        assertEquals(2, service1.getCount());
        assertEquals(1, service2.getCount());

        sendNotification("down", 2000);

        assertEquals(200, post("/getBankwithSession555555.xml")); // goes to service 2 because service 1 is down
        assertEquals(2, service1.getCount());
        assertEquals(2, service2.getCount());

        sendNotification("up", 4000);

        assertEquals(0, service3.getCount());
        assertEquals(200, post("/getBankwithSession666666.xml")); // goes to service 3
        assertEquals(2, service1.getCount());
        assertEquals(2, service2.getCount());
        assertEquals(1, service3.getCount());

        assertEquals(200, post("/getBankwithSession555555.xml")); // goes to service 2
        assertEquals(200, post("/getBankwithSession444444.xml")); // goes to service 2
        assertEquals(200, post("/getBankwithSession666666.xml")); // goes to service 3
        assertEquals(2, service1.getCount());
        assertEquals(4, service2.getCount());
        assertEquals(2, service3.getCount());
    }

    private static @NotNull DefaultRouter createRouter() {
        DefaultRouter node1 = new TestRouter();
        node1.getRegistry().register("global", new GlobalInterceptor());
        return node1;
    }

    private void startLB() throws Exception {

        LoadBalancingInterceptor lbi = new LoadBalancingInterceptor();
        lbi.setName("Default");
        XMLElementSessionIdExtractor extractor = new XMLElementSessionIdExtractor();
        extractor.setLocalName("session");
        extractor.setNamespace("http://predic8.com/session/");
        lbi.setSessionIdExtractor(extractor);

        ServiceProxy lbiRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3017), "thomas-bayer.com", 80);
        lbiRule.getFlow().add(lbi);

        ClusterNotificationInterceptor cni = new ClusterNotificationInterceptor();

        ServiceProxy cniRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3012), "thomas-bayer.com", 80);
        cniRule.getFlow().add(cni);

        lb = new DefaultRouter();
        lb.add(lbiRule);
        lb.add(cniRule);
        lb.start();
    }

    private DummyWebServiceInterceptor startNode(DefaultRouter node, int port) throws Exception {
        DummyWebServiceInterceptor service1 = new DummyWebServiceInterceptor();
        var global = node.getRegistry().getBean(GlobalInterceptor.class);
        global.orElseThrow().getFlow().add(service1);
        node.add(new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", port), "thomas-bayer.com", 80));
        node.start();
        return service1;
    }

    private HttpClient getClient() {
        HttpClient client = new HttpClient();
        client.getParams().setParameter(PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        return client;
    }

    private PostMethod getPostMethod(String request) {
        PostMethod post = new PostMethod("http://localhost:3017/axis2/services/BLZService");
        post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream(request)));
        post.setRequestHeader(CONTENT_TYPE, TEXT_XML_UTF8);
        post.setRequestHeader(SOAP_ACTION, "");
        return post;
    }

    private void sendNotification(String cmd, int port) throws IOException {
        PostMethod post = new PostMethod("http://localhost:3012/clustermanager/" + cmd + "?" +
                                         createQueryString("host", "localhost",
                                                 "port", String.valueOf(port)));
        new HttpClient().executeMethod(post);
    }

    private int post(String req) throws IOException {
        return getClient().executeMethod(getPostMethod(req));
    }

}
