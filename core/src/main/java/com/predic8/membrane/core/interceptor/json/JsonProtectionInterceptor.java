package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY;

@MCElement(name = "jsonProtection")
public class JsonProtectionInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonProtectionInterceptor.class);

    private ObjectMapper om = new ObjectMapper()
            .configure(FAIL_ON_READING_DUP_TREE_KEY, true)
            .configure(STRICT_DUPLICATE_DETECTION, true);

    private int maxTokens = 10000;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if ("GET".equals(exc.getRequest().getMethod()))
            return Outcome.CONTINUE;
        try {
            JsonParser parser = om.createParser(exc.getRequest().getBodyAsStreamDecoded());
            int tokenCount = 0;
            while (true) {
                JsonToken jsonToken = parser.nextValue();
                if (jsonToken == null)
                    break;
                tokenCount++;
                if (tokenCount > maxTokens)
                    throw new JsonParseException(parser, "Exceeded maxTokens (" + maxTokens + ").");
            }
        } catch (JsonParseException e) {
            LOG.error(e.getMessage());
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        return Outcome.CONTINUE;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * Maximum number of tokens a JSON document may consist of. For example, <code>{"a":"b"}</code> counts as 3.
     * @param maxTokens
     */
    @MCAttribute
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}
