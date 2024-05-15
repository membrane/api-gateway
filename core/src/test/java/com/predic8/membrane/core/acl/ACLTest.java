package com.predic8.membrane.core.acl;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.HttpTransport;

import java.util.function.Function;

import static java.util.regex.Pattern.compile;

public abstract class ACLTest extends AccessControlInterceptor {
    @Override
    public void init() throws Exception {}

    private AccessControlInterceptor createRouter(boolean isReverseDNS, boolean useForwardedFor, Function<Router, Resource> f) throws Exception {
        Router router = new Router();
        Transport transport = new HttpTransport();
        router.setTransport(transport);
        router.getTransport().setReverseDNS(isReverseDNS);
        AccessControlInterceptor aci = buildAci(f.apply(router), router);
        aci.setUseXForwardedForAsClientAddr(useForwardedFor);
        router.getTransport().getInterceptors().add(aci);
        router.init();
        return aci;
    }

    AccessControlInterceptor createIpACI(String scheme, ParseType ptype, boolean isReverseDNS, boolean useForwardedFor) throws Exception {
        return createRouter(isReverseDNS, useForwardedFor, router -> getIpResource(scheme, ptype, router));
    }

    AccessControlInterceptor createHostnameACI(String scheme, boolean isReverseDNS) throws Exception {
        return createRouter(isReverseDNS, false, router -> getHostnameResource(scheme, router));
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
