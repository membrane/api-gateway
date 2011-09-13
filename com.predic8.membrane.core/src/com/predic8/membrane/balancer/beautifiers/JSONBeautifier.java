package com.predic8.membrane.balancer.beautifiers;

import java.io.IOException;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;

public class JSONBeautifier {

	private ObjectMapper objectMapper = new ObjectMapper();
	
	private boolean indentOutput = true;
	
	private boolean allowedUnquotedFieldNames = true;
	
	private boolean quoteFieldNames = false;
	
	private boolean failOnUnknownProperties = true;
	
	public String beautify(byte[] content) throws IOException {
	    JsonNode tree = objectMapper.readTree(new String(content));
	    return objectMapper.defaultPrettyPrintingWriter().writeValueAsString(tree);
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
