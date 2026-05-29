package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.AudioRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.FilesRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.ImagesRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.OrganizationRequest;

import java.io.IOException;

public abstract class AbstractLLMProvider implements LLMProvider {

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) throws IOException {
        var uri = exchange.getRequest().getUri();
        if (uri.startsWith("/v1/chat/completions")) {
            return new ChatCompletionsRequest(exchange);
        }
        if (uri.startsWith("/v1/files")) {
            return new FilesRequest(exchange);
        }
        if (uri.contains("/v1/images")) {
            return new ImagesRequest(exchange);
        }
        if (uri.contains("/v1/audio")) {
            return new AudioRequest(exchange);
        }
        if (uri.contains("/v1/organization")) {
            return new OrganizationRequest(exchange);
        }
        throw new ReadingBodyException("Unknown request: " + uri);
    }
}
