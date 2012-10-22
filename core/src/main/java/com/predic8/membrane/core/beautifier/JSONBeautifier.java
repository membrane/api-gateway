/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package com.predic8.membrane.core.beautifier;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class JSONBeautifier {

	private ObjectMapper objectMapper = new ObjectMapper();
	
	private boolean indentOutput = true;
	
	private boolean allowedUnquotedFieldNames = true;
	
	private boolean quoteFieldNames = false;
	
	private boolean failOnUnknownProperties = true;
	
	public String beautify(String content) throws IOException {
	    JsonNode tree = objectMapper.readTree(content);
	    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
	}

	public void configure() {
		objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, indentOutput);
	    objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, allowedUnquotedFieldNames);
	    objectMapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, quoteFieldNames);
	    objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);
	}
	
	public void setIndentOutput(boolean indentOutput) {
		this.indentOutput = indentOutput;
	}

	public void setAllowedUnquotedFieldNames(boolean allowedUnquotedFieldNames) {
		this.allowedUnquotedFieldNames = allowedUnquotedFieldNames;
	}

	public void setQuoteFieldNames(boolean quoteFieldNames) {
		this.quoteFieldNames = quoteFieldNames;
	}
	
	public void setFailOnUnknownProperties(boolean failOnUnknownProperties) {
		this.failOnUnknownProperties = failOnUnknownProperties;
	}
	
}
