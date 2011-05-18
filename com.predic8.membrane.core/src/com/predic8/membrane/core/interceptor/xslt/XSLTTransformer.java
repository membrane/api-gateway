package com.predic8.membrane.core.interceptor.xslt;

import static com.predic8.membrane.core.util.TextUtil.isNullOrEmpty;

import java.io.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import org.apache.commons.logging.*;

public class XSLTTransformer {
	private static Log log = LogFactory.getLog(XSLTTransformer.class.getName());
	
	private TransformerFactory fac = TransformerFactory.newInstance();
	
	public String transform(String ss, Source xml) throws Exception {		
		log.debug("applying transformation: "+ss);		
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
