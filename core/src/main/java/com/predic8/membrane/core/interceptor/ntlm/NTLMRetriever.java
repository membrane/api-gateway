package com.predic8.membrane.core.interceptor.ntlm;

import com.predic8.membrane.core.exchange.Exchange;

public interface NTLMRetriever {
    String fetchUsername(Exchange exc);
    String fetchPassword(Exchange exc);
    String fetchDomain(Exchange exc);
    String fetchWorkstation(Exchange exc);
}
