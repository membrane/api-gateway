package com.predic8.membrane.core.interceptor.authentication.xen;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;

public interface SessionIdAccessor {

    String getSessionId(Exchange exchange, Interceptor.Flow flow);

    void replaceSessionId(Exchange exchange, String newSessionId, Interceptor.Flow flow);

}
