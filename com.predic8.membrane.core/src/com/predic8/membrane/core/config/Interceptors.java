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

package com.predic8.membrane.core.config;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;

public class Interceptors extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "interceptors";

	private List<Interceptor> interceptors = new ArrayList<Interceptor>();

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (AbstractInterceptor.ELEMENT_NAME.equals(child)) {
			AbstractInterceptor inter = (AbstractInterceptor) (new AbstractInterceptor()).parse(token);
			String id = inter.getId();
			Interceptor interceptor = Router.getInstance().getInterceptorFor(id);
			try {
				interceptors.add(interceptor);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		for (Interceptor interceptor : interceptors) {
			interceptor.write(out);
		}
		out.writeEndElement();
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

}
