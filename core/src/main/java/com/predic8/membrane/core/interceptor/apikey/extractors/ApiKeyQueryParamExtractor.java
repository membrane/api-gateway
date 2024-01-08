package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.URIFactory;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static com.predic8.membrane.core.util.URLParamUtil.getParams;
import static java.lang.String.CASE_INSENSITIVE_ORDER;

@MCElement(name="queryParamExtractor", topLevel = false)
public class ApiKeyQueryParamExtractor implements ApiKeyExtractor{

    private String paramName = "ApiKey";

    @Override
    public Optional<String> extract(Exchange exc) {
        Map<String, String> queryParams;
        try {
            queryParams = new TreeMap<>(CASE_INSENSITIVE_ORDER);
            queryParams.putAll(getParams(new URIFactory(), exc, ERROR));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (queryParams.containsKey(paramName.toLowerCase())) {
            return Optional.of(queryParams.get(paramName.toLowerCase()));
        }

        return Optional.empty();
    }



    @MCAttribute(attributeName = "name")
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getParamName() {
        return paramName;
    }
}