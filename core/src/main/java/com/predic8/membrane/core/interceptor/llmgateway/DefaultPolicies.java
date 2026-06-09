/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.ModelInputRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.OrganizationRequest;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

/**
 * @description LLM Gateway policies for token usage and model restrictions.
 */
@MCElement(name = "policies", id = "llm-gateway-policies")
public class DefaultPolicies implements Policies {

    private static final Logger log = LoggerFactory.getLogger(DefaultPolicies.class);

    private LLMErrorCreator errorCreator;

    private List<String> models;
    private int maxOutputTokens;
    private int maxInputTokens;

    public void init(LLMErrorCreator errorCreator) {
        this.errorCreator = errorCreator;
    }

    public Outcome handleRequest(ModelInputRequest mir, Exchange exc) {

        if (mir instanceof OrganizationRequest) {
            return CONTINUE;
        }

        var outcome = checkTokenLimits(mir, exc);
        if (outcome != CONTINUE) {
            return outcome;
        }
        return checkModel(mir, exc);
    }

    public Outcome checkModel(ModelInputRequest mir, Exchange exc) {
        var model = mir.getModel();
        if (models != null && !models.contains(model)) {
            exc.setResponse(errorCreator.modelNotAllowed(model, models));
            return RETURN;
        }
        return CONTINUE;
    }

    public Outcome checkTokenLimits(ModelInputRequest mir, Exchange exc) {

        var requestedMaxOutputTokens = mir.getRequestedMaxOutputTokens();
        var inputTokens = mir.estimateInputTokens();

        if (maxOutputTokens > 0) {
            if (requestedMaxOutputTokens <= 0) {
                log.info("No max. output requested. Setting limit to {}.", maxOutputTokens);
                mir.setMaxOutputTokens(maxOutputTokens);
            } else if (requestedMaxOutputTokens > maxOutputTokens) {
                log.info("Requested max. output tokens {} exceed the limit. Setting limit to {}.", requestedMaxOutputTokens, maxOutputTokens);
                mir.setMaxOutputTokens(maxOutputTokens);
            }
        }

        if (maxInputTokens != 0) {
            if (inputTokens > maxInputTokens) {
                log.info("Input tokens {} exceed the limit of {}.", inputTokens, maxInputTokens);
                exc.setResponse(errorCreator.inputTokensExceeded(maxInputTokens, inputTokens));
                return RETURN;
            }
        }
        return CONTINUE;
    }

    public List<String> getModels() {
        return models;
    }

    /**
     * @param models List of models that can be used by the gateway.
     * @description Restricts the models that can be used by the gateway.
     * @default null (no restriction)
     */
    @MCAttribute
    public void setModels(List<String> models) {
        this.models = models;
    }


    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * @param maxOutputTokens Maximum number of tokens the LLM should use to generate a response.
     * @description Maximum number of tokens the LLM should use to generate a response. This is just a hint that the gateway
     * sends to the LLM provider. The provider may use a different limit.
     * @default 0 (unlimited)
     */
    @MCAttribute
    public void setMaxOutputTokens(int maxOutputTokens) {
        if (maxOutputTokens < 0) {
            throw new ConfigurationException("maxOutputTokens must be >= 0");
        }
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    /**
     * @param maxInputTokens Maximum number of tokens that a request can use.
     * @description Restricts token usage for the input. The size of the input is estimated by gateway based on the request size.
     * Actual token usage may be deviate from this value.
     */
    @MCAttribute
    public void setMaxInputTokens(int maxInputTokens) {
        if (maxInputTokens < 0) {
            throw new ConfigurationException("maxInputTokens must be >= 0");
        }
        this.maxInputTokens = maxInputTokens;
    }
}
