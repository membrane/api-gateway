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
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Limits what a client may ask of the model: how large the input may be, how much the model may generate,
 * and which models it may use. A request over the input limit or for a model that is not allowed is rejected with an
 * error in the format of the configured provider; the output limit is written into the request instead of rejecting it.
 * See tutorials/ai/llm-gateway/openai/10-Basic-LLM-Gateway.yaml.
 * <pre>
 * policies:
 *   [ maxInputTokens: &lt;count&gt; ]
 *   [ maxOutputTokens: &lt;count&gt; ]
 *   [ models: ]
 *     - &lt;model&gt;
 *     ...
 * </pre>
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         openai: {}
 *         policies:
 *           maxInputTokens: 100
 *           maxOutputTokens: 200
 *           models:
 *             - gpt-5-mini
 *   target:
 *     url: https://api.openai.com
 * </code></pre>
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
            return ABORT;
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
                return ABORT;
            }
        }
        return CONTINUE;
    }

    public List<String> getModels() {
        return models;
    }

    /**
     * @description The models the gateway accepts. A request asking for any other model is rejected.
     * @default (no restriction)
     * @example gpt-5-mini
     */
    @MCAttribute
    public void setModels(List<String> models) {
        this.models = models;
    }


    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * @description Caps how many tokens the model may generate. The gateway lowers the limit a client asked for and
     * sets one where the client asked for none, but the provider decides what it honours.
     * @default 0 (unlimited)
     * @example 200
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
     * @description Rejects a request whose input exceeds this number of tokens. The gateway estimates the input from
     * the size of the request before it is forwarded, so the number the provider counts may differ.
     * @default 0 (unlimited)
     * @example 100
     */
    @MCAttribute
    public void setMaxInputTokens(int maxInputTokens) {
        if (maxInputTokens < 0) {
            throw new ConfigurationException("maxInputTokens must be >= 0");
        }
        this.maxInputTokens = maxInputTokens;
    }
}
