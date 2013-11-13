/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.http.xml;

import com.predic8.membrane.core.config.AbstractXmlElement;

public abstract class Message extends AbstractXmlElement {

	protected Headers headers;
	protected AbstractXmlElement body;

	public Message() {
	}
	
	public Message(com.predic8.membrane.core.http.Message message) {
		setHeaders(new Headers(message.getHeader()));
		
		if (message.isXML()) {
			body = new XMLBody(message);
		} else if (message.isJSON()) {
			body = new JSONBody(message);
		} else {
			body = new PlainBody(message);
		}

	}
	
	public Headers getHeaders() {
		return headers;
	}

	public void setHeaders(Headers headers) {
		this.headers = headers;
	}

	public AbstractXmlElement getBody() {
		return body;
	}
	
	public void setBody(AbstractXmlElement body) {
		this.body = body;
	}

}
