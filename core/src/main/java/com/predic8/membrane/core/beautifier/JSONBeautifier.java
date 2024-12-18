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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import java.io.*;

import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.fasterxml.jackson.databind.SerializationFeature.*;

public class JSONBeautifier {

	private final ObjectMapper om = new ObjectMapper();

	public String beautify(String content) throws IOException {
		return om.writerWithDefaultPrettyPrinter().writeValueAsString(om.readTree(content));
	}

	public void configure() {
		om.configure(INDENT_OUTPUT, true);
		om.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
		om.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
		om.configure(FAIL_ON_UNKNOWN_PROPERTIES, true);
	}
}
