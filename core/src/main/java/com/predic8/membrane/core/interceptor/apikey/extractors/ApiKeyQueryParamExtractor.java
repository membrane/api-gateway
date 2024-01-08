package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static java.lang.String.*;
import static java.util.Optional.*;

@MCElement(name="queryParamExtractor", topLevel = false)
public class ApiKeyQueryParamExtractor implements ApiKeyExtractor{

    private static final Logger log = LoggerFactory.getLogger(ApiKeyQueryParamExtractor.class);

    private String paramName = "api-key";

    @Override
    public Optional<String> extract(Exchange exc) {
        Map<String, String> queryParams;
        try {
            queryParams = new TreeMap<>(CASE_INSENSITIVE_ORDER); // Handle key names case insensitive
            queryParams.putAll(getParams(new URIFactory(), exc, ERROR));
        } catch (Exception e) {
            log.info("Error extracting query parameters. From " + exc.getRequest().getUri());
            return empty();
        }

        if (queryParams.containsKey(paramName.toLowerCase())) {
            return Optional.of(queryParams.get(paramName.toLowerCase()));
        }

        return empty();
    }



    @MCAttribute(attributeName = "name")
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    @SuppressWarnings("unused")
    public String getParamName() {
        return paramName;
    }
}