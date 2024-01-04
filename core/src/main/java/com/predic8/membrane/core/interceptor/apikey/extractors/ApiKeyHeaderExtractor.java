package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderName;

import java.util.*;

@MCElement(name="apiKeyHeaderExtractor",topLevel=false)
public class ApiKeyHeaderExtractor implements ApiKeyExtractor{

    private HeaderName headerName = new HeaderName("X-API-KEY");

    @Override
    public Optional<String> extract(Exchange exc) {
        Header header = exc.getRequest().getHeader();

        if (header.contains(headerName)) {
            return Optional.of(header.getFirstValue(headerName));
        }

        return Optional.empty();
    }

    @MCAttribute(attributeName = "name")
    public void setHeaderName(String headerName) {
        this.headerName = new HeaderName(headerName);
    }

    public HeaderName getHeaderName() {
        return headerName;
    }
}
