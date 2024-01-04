package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.*;

public interface ApiKeyExtractor {
    Optional<String> extract(Exchange exc);
}
