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

package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import static com.predic8.membrane.annot.yaml.error.LineYamlErrorRenderer.renderErrorReport;

public class ConfigurationParsingException extends RuntimeException {

    private ParsingContext<?> parsingContext;
    private JsonNode wrong;

    public ConfigurationParsingException(String message) {
        super(message);
    }

    public ConfigurationParsingException(Throwable cause) {
        super(cause);
    }

    public ConfigurationParsingException(String message, Throwable cause, ParsingContext<?> pc) {
        super(message, cause);
        this.parsingContext = pc;
    }

    public ParsingContext<?> getParsingContext() {
        return parsingContext;
    }

    public void setParsingContext(ParsingContext<?> parsingContext) {
        this.parsingContext = parsingContext;
    }

    /**
     * Returns a complete formatted error report including highlighted YAML.
     */
    public String getFormattedReport() throws JsonProcessingException {
        return renderErrorReport(this.parsingContext);
    }

    public JsonNode getWrong() {
        return wrong;
    }

    public void setWrong(JsonNode wrong) {
        this.wrong = wrong;
    }
}