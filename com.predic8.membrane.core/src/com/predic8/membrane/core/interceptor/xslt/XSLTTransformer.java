package com.predic8.membrane.core.interceptor.xslt;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import static com.predic8.membrane.core.util.TextUtil.*;

public class XSLTTransformer {
	private TransformerFactory fac = TransformerFactory.newInstance();
	
	public String transform(String ss, Source xml) throws Exception {
		StringWriter sw = new StringWriter();
		getTransformer(ss).transform(xml, new StreamResult(sw));
		return sw.toString();
	}

	private Transformer getTransformer(String ss)
			throws TransformerConfigurationException {
		
		if (isNullOrEmpty(ss)) return fac.newTransformer();
		
		return fac.newTransformer(getStylesheetSource(ss));
	}
	
	private StreamSource getStylesheetSource(String name) {
		if (name.startsWith("classpath:"))
			return new StreamSource(getClass().getResourceAsStream(name.substring(10)));
		
		if ( new File(name).isAbsolute() )
			return new StreamSource(new File(name));
		
		return new StreamSource(new File(System.getenv("MEMBRANE_HOME"), name));
	}
}
