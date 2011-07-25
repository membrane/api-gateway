package com.predic8.membrane.core.interceptor.balancer;

import static com.predic8.membrane.core.util.HttpUtil.createResponse;
import static com.predic8.membrane.core.util.URLUtil.parseQueryString;

import java.util.*;
import java.util.regex.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.stream.*;

import org.apache.commons.codec.binary.*;
import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.*;

public class ClusterNotificationInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory
			.getLog(ClusterNotificationInterceptor.class.getName());

	private Pattern urlPattern = Pattern.compile("/clustermanager/(up|down|takeout)/?\\??(.*)");

	private boolean validateSignature = false;
	private int timeout = 0;
	private String keyHex;
	
	public ClusterNotificationInterceptor() {
		name = "ClusterNotifcationInterceptor";
		priority = 3000;
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug(exc.getOriginalRequestUri());

		Matcher m = urlPattern.matcher(exc.getOriginalRequestUri());
		
		if (!m.matches()) return Outcome.CONTINUE;
		
		log.debug("request received: "+m.group(1));
		if (validateSignature && !getParams(exc).containsKey("data")) {
			exc.setResponse(createResponse(403, "Forbidden", null, null));
			return Outcome.ABORT;			
		}
		
		Map<String, String> params = validateSignature?
				getDecryptedParams(getParams(exc).get("data")):
				getParams(exc);

		if ( isTimedout(params) ) {
			exc.setResponse(createResponse(403, "Forbidden", null, null));
			return Outcome.ABORT;
		}
		
		updateClusterManager(m, params);
		
		exc.setResponse(createResponse(204, "No Content", null, null));
		return Outcome.ABORT;
	}

	private void updateClusterManager(Matcher m, Map<String, String> params)
			throws Exception {
		if ("up".equals(m.group(1))) {
			router.getClusterManager().up(
					getClusterParam(params),
					params.get("host"), 
					getPortParam(params));			
		} else if ("down".equals(m.group(1))) {
			router.getClusterManager().down(
					getClusterParam(params),
					params.get("host"), 
					getPortParam(params));			
		} else {
			router.getClusterManager().takeout(
					getClusterParam(params),
					params.get("host"), 
					getPortParam(params));			
		}
	}

	private boolean isTimedout(Map<String, String> params) {
		return timeout > 0 && System.currentTimeMillis()-Long.parseLong(params.get("time")) > timeout;
	}

	private Map<String, String> getDecryptedParams(String data) throws Exception {
		Cipher cipher = Cipher.getInstance("AES");
		SecretKeySpec skeySpec = new SecretKeySpec(Hex.decodeHex(keyHex.toCharArray()), "AES");
		cipher.init(Cipher.DECRYPT_MODE, skeySpec);
	    return parseQueryString(new String(cipher.doFinal(Base64.decodeBase64(data.getBytes("UTF-8"))),"UTF-8"));						
	}

	private int getPortParam(Map<String, String> params) throws Exception {
		return Integer.parseInt(params.get("port"));
	}

	private String getClusterParam(Map<String, String> params) throws Exception {
		return params.get("cluster") == null ? "Default" : params.get("cluster");
	}

	private Map<String, String> getParams(Exchange exc) throws Exception {
		String uri = exc.getOriginalRequestUri();
		int qStart = uri.indexOf('?');
		if (qStart == -1 || qStart + 1 == uri.length())
			return new HashMap<String, String>();
		return parseQueryString(exc.getOriginalRequestUri().substring(
				qStart + 1));
	}

	public boolean isValidateSignature() {
		return validateSignature;
	}

	public void setValidateSignature(boolean validateSignature) {
		this.validateSignature = validateSignature;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public String getKeyHex() {
		return keyHex;
	}

	public void setKeyHex(String keyHex) {
		this.keyHex = keyHex;
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("clusterNotification");
		
		out.writeAttribute("keyHex", keyHex);		
		out.writeAttribute("validateSignature", ""+validateSignature);		
		out.writeAttribute("timeout", ""+timeout);		
		
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {

		if ( token.getAttributeValue("", "keyHex") != null ) 
			keyHex = token.getAttributeValue("", "keyHex");
		
		if ( token.getAttributeValue("", "validateSignature") != null ) 
			validateSignature = Boolean.parseBoolean(token.getAttributeValue("", "validateSignature"));

		if ( token.getAttributeValue("", "timeout") != null ) 
			timeout = Integer.parseInt(token.getAttributeValue("", "timeout"));
	}	
}
