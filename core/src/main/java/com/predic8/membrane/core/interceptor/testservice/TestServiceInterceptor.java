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
package com.predic8.membrane.core.interceptor.testservice;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXParseException;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.URLUtil;
import com.predic8.membrane.core.ws.relocator.Relocator.PathRewriter;

@MCElement(name="testService")
public class TestServiceInterceptor extends AbstractInterceptor {
	
	private static String SOAP_VERSION = "soap_version";
	private static Pattern WSDL = Pattern.compile("\\?WSDL", Pattern.CASE_INSENSITIVE);
	private static final Pattern RELATIVE_PATH_PATTERN = Pattern.compile("^./[^/?]*\\?");

	private WSDLInterceptor wi = new WSDLInterceptor();
	
	@Override
	public void init(Router router) throws Exception {
		super.init(router);
		wi.init(router);

		Rule r = router.getParentProxy(this);
		if (r instanceof AbstractServiceProxy) {
			final Path path = ((AbstractServiceProxy) r).getPath();
			if (path != null) {
				if (path.isRegExp())
					throw new Exception("<testService> may not be used together with <path isRegExp=\"true\">.");
				wi.setPathRewriter(new PathRewriter() {
					@Override
					public String rewrite(String path2) {
						try {
							String keyPath = path.getValue();
							if (path2.contains("://")) {
								path2 = new URL(new URL(path2), keyPath).toString();
							} else {
								Matcher m = RELATIVE_PATH_PATTERN.matcher(path2);
								path2 = m.replaceAll("./" + URLUtil.getName(keyPath) + "?");
							}
						} catch (MalformedURLException e) {
						}
						return path2;
					}
				});
			}
		}
		
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (WSDL.matcher(exc.getRequest().getUri()).find()) {
			exc.setResponse(Response.ok().
					header(Header.SERVER, Constants.PRODUCT_NAME + " " + Constants.VERSION).
					header(Header.CONTENT_TYPE, MimeType.TEXT_XML).
					body(getClass().getResourceAsStream("the.wsdl"), true).
					build());
			wi.handleResponse(exc);
			return Outcome.RETURN;
		}
		
		
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			dbf.setIgnoringComments(true);
			dbf.setIgnoringElementContentWhitespace(true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document d = db.parse(exc.getRequest().getBodyAsStreamDecoded());
			exc.setResponse(createResponse(exc, d));
		} catch (SAXParseException e) {
			exc.setResponse(createResponse(e, exc.getProperty(SOAP_VERSION) == null));
		} catch (AssertionError e) {
			exc.setResponse(createResponse(e, exc.getProperty(SOAP_VERSION) == null));
		}
		return Outcome.RETURN;
	}

	private Response createResponse(Throwable e, boolean useSoap11) {
		String title = "Internal Server Error";
		String message = e.getMessage();
		String body = useSoap11 ? HttpUtil.getFaultSOAPBody(title, message) : HttpUtil.getFaultSOAP12Body(title,
				message);
		return Response.interalServerError().
				header(Header.SERVER, Constants.PRODUCT_NAME + " " + Constants.VERSION).
				header(HttpUtil.createHeaders(MimeType.TEXT_XML_UTF8)).
				body(body.getBytes(Constants.UTF_8_CHARSET)).
				build();
	}

	private Response createResponse(Exchange exc, Document d) {
		Element envelope = d.getDocumentElement();

		if (envelope == null)
			throw new AssertionError("No SOAP <Envelope> found.");
		if (!envelope.getLocalName().equals("Envelope"))
			throw new AssertionError("No SOAP Envelope found.");
		if (envelope.getNamespaceURI().equals(Constants.SOAP11_NS))
			return handleSOAP11(envelope);
		if (envelope.getNamespaceURI().equals(Constants.SOAP12_NS)) {
			exc.setProperty(SOAP_VERSION, "1.2");
			return handleSOAP12(envelope);
		}
		throw new AssertionError("Unknown SOAP version.");
		
	}

	private Response handleSOAP11(Element envelope) {
		Element body = null;
		NodeList children = envelope.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Text) {
				String text = ((Text) children.item(i)).getNodeValue();
				for (int j = 0; j < text.length(); j++)
					if (!Character.isWhitespace(text.charAt(j)))
						throw new AssertionError("Found non-whitespace text.");
				continue;
			}
			if (!(children.item(i) instanceof Element))
				throw new AssertionError("Non-element child of <Envelope> found: " + children.item(i).getNodeName() + ".");
			Element item = (Element) children.item(i);
			if (!item.getNamespaceURI().equals(Constants.SOAP11_NS))
				throw new AssertionError("Non-SOAP child element of <Envelope> found.");
			if (item.getLocalName().equals("Body"))
				body = item;
		}
		if (body == null)
			throw new AssertionError("No SOAP <Body> found.");
		
		children = body.getChildNodes();
		Element operation = null;
		
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Text) {
				String text = ((Text) children.item(i)).getNodeValue();
				for (int j = 0; j < text.length(); j++)
					if (!Character.isWhitespace(text.charAt(j)))
						throw new AssertionError("Found non-whitespace text.");
				continue;
			}
			if (!(children.item(i) instanceof Element))
				throw new AssertionError("Non-element child of <Body> found: " + children.item(i).getNodeName() + ".");
			operation = (Element) children.item(i);
		}
		if (operation == null)
			throw new AssertionError("No SOAP <Body> found.");
		
		return handleOperation(operation, true);
	}

	private Response handleSOAP12(Element envelope) {
		Element body = null;
		NodeList children = envelope.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Text) {
				String text = ((Text) children.item(i)).getNodeValue();
				for (int j = 0; j < text.length(); j++)
					if (!Character.isWhitespace(text.charAt(j)))
						throw new AssertionError("Found non-whitespace text.");
				continue;
			}
			if (!(children.item(i) instanceof Element))
				throw new AssertionError("Non-element child of <Envelope> found: " + children.item(i).getNodeName() + ".");
			Element item = (Element) children.item(i);
			if (!item.getNamespaceURI().equals(Constants.SOAP12_NS))
				throw new AssertionError("Non-SOAP child element of <Envelope> found.");
			if (item.getLocalName().equals("Body"))
				body = item;
		}
		if (body == null)
			throw new AssertionError("No SOAP <Body> found.");
		
		children = body.getChildNodes();
		Element operation = null;
		
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Text) {
				String text = ((Text) children.item(i)).getNodeValue();
				for (int j = 0; j < text.length(); j++)
					if (!Character.isWhitespace(text.charAt(j)))
						throw new AssertionError("Found non-whitespace text.");
				continue;
			}
			if (!(children.item(i) instanceof Element))
				throw new AssertionError("Non-element child of <Body> found: " + children.item(i).getNodeName() + ".");
			operation = (Element) children.item(i);
		}
		if (operation == null)
			throw new AssertionError("No SOAP <Body> found.");
		
		return handleOperation(operation, false);
	}

	private Response handleOperation(Element operation, boolean soap11) {
		if (!operation.getNamespaceURI().equals("http://thomas-bayer.com/blz/"))
			throw new AssertionError("Unknown operation namespace.");
		
		if (operation.getLocalName().equals("getBank")) {
			NodeList children = operation.getChildNodes();
			Element param = null;
			for (int i = 0; i < children.getLength(); i++) {
				if (children.item(i) instanceof Text) {
					String text = ((Text) children.item(i)).getNodeValue();
					for (int j = 0; j < text.length(); j++)
						if (!Character.isWhitespace(text.charAt(j)))
							throw new AssertionError("Found non-whitespace text.");
					continue;
				}
				if (!(children.item(i) instanceof Element))
					throw new AssertionError("Non-element child of <Body> found: " + children.item(i).getNodeName() + ".");
				param = (Element) children.item(i);
			}
			if (param == null)
				throw new AssertionError("No parameter child of operation element found.");
			
			if (!param.getNamespaceURI().equals("http://thomas-bayer.com/blz/") || !param.getLocalName().equals("blz"))
				throw new AssertionError("Unknown parameter element.");

			children = param.getChildNodes();
			if (children.getLength() != 1)
				throw new AssertionError("Parameter element has children.length != 1");
			if (!(children.item(0) instanceof Text))
				throw new AssertionError("Parameter element has non-text child.");
			
			Text text = (Text)children.item(0);
			
			String blz = text.getNodeValue();
			
			return getBank(blz, soap11);
		} else {
			throw new AssertionError("Unknown operation.");
		}
	}

	private Response getBank(String blz, boolean soap11) {
		if (blz.equals("38060186")) {
			return respondBank("Volksbank Bonn Rhein-Sieg", "GENODED1BRS", "Bonn", "53015", soap11);
		} else {
			throw new AssertionError("Keine Bank gefunden.");
		}
		
	}

	private String escape(String s) {
		return s.replace("&", "&amp;").replace(">", "&gt;").replace("<", "&lt;");
	}
	
	private Response respondBank(String bezeichnung, String bic, String ort, String plz, boolean soap11) {
		String ns = soap11 ? "http://schemas.xmlsoap.org/soap/envelope/" : "http://www.w3.org/2003/05/soap-envelope";
		String body = "<soapenv:Envelope xmlns:soapenv=\"" + ns + "\"><soapenv:Body>"+
				"<ns1:getBankResponse xmlns:ns1=\"http://thomas-bayer.com/blz/\"><ns1:details><ns1:bezeichnung>"+
				escape(bezeichnung) + "</ns1:bezeichnung><ns1:bic>" + escape(bic) + "</ns1:bic><ns1:ort>" + escape(ort) +
				"</ns1:ort><ns1:plz>" + escape(plz) +
				"</ns1:plz></ns1:details></ns1:getBankResponse></soapenv:Body></soapenv:Envelope>";
		return Response.ok().
				header(Header.SERVER, Constants.PRODUCT_NAME + " " + Constants.VERSION).
				header(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8).
				body(body.getBytes(Constants.UTF_8_CHARSET)).
				build();
	}

}
