package com.predic8.membrane.balancer.beautifiers;

import java.io.IOException;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;

public class JSONBeautifier {

	// DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES
	
	public String beautify(byte[] content) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
	    objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
	    objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
	    objectMapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
	    JsonNode tree = objectMapper.readTree(new String(content));
	    return objectMapper.defaultPrettyPrintingWriter().writeValueAsString(tree);
	}
	
}
