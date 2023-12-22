package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.exchange.Exchange;

public interface ApiKeyExtractor {

    public String extract(Exchange exc);

}
