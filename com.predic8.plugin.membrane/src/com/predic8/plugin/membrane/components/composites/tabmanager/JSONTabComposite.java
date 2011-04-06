/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.plugin.membrane.components.composites.tabmanager;

import java.io.IOException;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.eclipse.swt.widgets.TabFolder;


public class JSONTabComposite extends BodyTextTabComposite {

	public static final String TAB_TITLE = "JSON";
	
	public JSONTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
	}

	@Override
	public void beautify(byte[] content, String encoding) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
	    objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
	    JsonNode tree = objectMapper.readTree(new String(content));
	    bodyText.setText(objectMapper.writeValueAsString(tree));
	    bodyText.redraw();
	}
	
	
}
