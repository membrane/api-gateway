package com.predic8.membrane.core.interceptor.json;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@SuppressWarnings("unused")
@MCElement(name="jsonPathReplacer")
public class ReplaceInterceptor extends AbstractInterceptor {

    private String jsonPath;

    private String replacement;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if(exc.getRequestContentType().equals(APPLICATION_JSON)) {
            exc.getRequest().setBodyContent(replaceWithJsonPath(exc, jsonPath, replacement).getBytes());
        }
        return CONTINUE;
    }

    String replaceWithJsonPath(Exchange exc, String jsonPath, String replacement) {
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(exc.getRequest().getBodyAsStringDecoded());
        document = JsonPath.parse(document).set(jsonPath, replacement).json();
        return Configuration.defaultConfiguration().jsonProvider().toJson(document);
    }

    @MCAttribute
    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    @MCAttribute
    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public String getJsonPath() {return jsonPath;}


    public String getReplacement() {return replacement;}


}
