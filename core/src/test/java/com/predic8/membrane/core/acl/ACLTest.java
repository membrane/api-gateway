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
package com.predic8.membrane.core.acl;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.util.DNSCache;
import org.mockito.MockedStatic;

import java.net.InetAddress;
import java.util.function.*;

import static java.util.regex.Pattern.*;
import static org.mockito.Mockito.*;

public abstract class ACLTest extends AccessControlInterceptor {

    private AccessControlInterceptor createInterceptor(boolean isReverseDNS, boolean useForwardedFor, Function<Router, Resource> f) {
        Router router = mock(Router.class);
        DNSCache dnsCache = mock(DNSCache.class);

        when(router.getDnsCache()).thenReturn(dnsCache);
        when(dnsCache.getCanonicalHostName(argThat(inetAddress ->
                inetAddress != null && inetAddress.getHostAddress().equals("127.0.0.1"))))
                .thenReturn("localhost");
        when(dnsCache.getCanonicalHostName(argThat(inetAddress ->
                inetAddress != null && !inetAddress.getHostAddress().equals("127.0.0.1"))))
                .thenCallRealMethod();

        AccessControlInterceptor aci = buildAci(f.apply(router), router);
        aci.setUseXForwardedForAsClientAddr(useForwardedFor);
        return aci;
    }

    AccessControlInterceptor createIpACI(String scheme, ParseType ptype, boolean isReverseDNS, boolean useForwardedFor) {
        return createInterceptor(isReverseDNS, useForwardedFor, router -> getIpResource(scheme, ptype, router));
    }

    AccessControlInterceptor createHostnameACI(String scheme, boolean isReverseDNS) throws Exception {
        return createInterceptor(isReverseDNS, false, router -> getHostnameResource(scheme, router));
    }

    private static AccessControlInterceptor buildAci(Resource resource, Router router) {
        return new ACLTest() {{
            setAccessControl(new AccessControl(router) {{
                addResource(resource);
            }});
        }};
    }

    private static Resource getIpResource(String scheme, ParseType ptype, Router router) {
        return createResource(new Ip(router) {{
            setParseType(ptype);
            setSchema(scheme);
        }}, router);
    }

    private static Resource getHostnameResource(String scheme, Router router) {
        return createResource(new Hostname(router) {{
            setSchema(scheme);
        }}, router);
    }

    private static Resource createResource(AbstractClientAddress address, Router router) {
        return new Resource(router) {{
            addAddress(address);
            setUriPattern(compile(".*"));
        }};
    }
}
