package com.predic8.membrane.servlet.test;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCMain(
		outputPackage="com.predic8.membrane.servlet.test.config.spring",
		outputName="router-conf.xsd",
		xsd="" +
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
				"<xsd:schema xmlns=\"http://membrane-soa.org/war-test/1/\"\r\n" + 
				"	xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:beans=\"http://www.springframework.org/schema/beans\"\r\n" + 
				"	targetNamespace=\"http://membrane-soa.org/war-test/1/\"\r\n" + 
				"	elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">\r\n" + 
				"\r\n" + 
				"	<xsd:import namespace=\"http://www.springframework.org/schema/beans\" schemaLocation=\"http://www.springframework.org/schema/beans/spring-beans-3.1.xsd\" />\r\n" + 
				"\r\n" + 
				"${declarations}\r\n" +
				"\r\n" +
				"${raw}\r\n" +
				"	\r\n" + 
				"	<xsd:complexType name=\"EmptyElementType\">\r\n" + 
				"		<xsd:complexContent>\r\n" + 
				"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"				<xsd:sequence />\r\n" + 
				"			</xsd:extension>\r\n" + 
				"		</xsd:complexContent>\r\n" + 
				"	</xsd:complexType>\r\n" + 
				"	\r\n" + 
				"</xsd:schema>")
@MCElement(name="echo", configPackage="com.predic8.membrane.servlet.test.config.spring")
public class EchoInterceptor extends AbstractInterceptor {
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Outcome outcome = exc.echo();
		exc.getResponse().getHeader().removeFields(Header.CONTENT_LENGTH);
		String body = exc.getRequest().getUri() + "\n" + new String(exc.getRequest().getBody().getContent(), Constants.UTF_8_CHARSET);
		exc.getResponse().setBodyContent(body.getBytes(Constants.UTF_8_CHARSET));
		return outcome;
	}

}
