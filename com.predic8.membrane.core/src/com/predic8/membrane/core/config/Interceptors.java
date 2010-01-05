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
