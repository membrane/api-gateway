package com.predic8.membrane.core.interceptor.adminApi;

import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminApiInterceptorTest {

    @Test
    void generateTargetFromHostPort() {
        AbstractServiceProxy p = new ServiceProxy() {{
            setTarget(new Target() {{
                setHost("localhost");
                setPort(8080);
            }});
        }};
        assertEquals("localhost:8080", AdminApiInterceptor.generateTarget(p));
    }

    @Test
    void generateTargetFromURL() {
        AbstractServiceProxy p = new ServiceProxy() {{
            setTarget(new Target() {{
                setUrl("https://www.predic8.de");
            }});
        }};
        assertEquals("https://www.predic8.de", AdminApiInterceptor.generateTarget(p));
    }

    @Test
    void generateProxyFromHostPortPath() {
        AbstractServiceProxy p = new ServiceProxy() {{
            setHost("localhost");
            setPort(8080);
            setPath(new Path(false, "/wow"));
        }};
        assertEquals("localhost:8080/wow", AdminApiInterceptor.generateProxy(p));
    }

    @Test
    void generateProxyFromHostPort() {
        AbstractServiceProxy p = new ServiceProxy() {{
            setHost("localhost");
            setPort(8080);
            setPath(new Path());
        }};
        assertEquals("localhost:8080/", AdminApiInterceptor.generateProxy(p));
    }
}