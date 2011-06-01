package com.predic8.membrane.core.interceptor.balancer;

import static com.predic8.membrane.core.util.URLUtil.parseQueryString;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.regex.*;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;

public class ClusterNotificationInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory
			.getLog(ClusterNotificationInterceptor.class.getName());

	private Pattern pattern = Pattern.compile("/clustermanager/(up|down)/?\\??(.*)");

	private boolean validateSignature = false;
	private int timeout = 0;
	private String keyStore = "configuration/membrane.jks";
	private String keyPass = "secret";
	private String storePass = "secret";
	private String keyAlias = "membrane";
	
	public ClusterNotificationInterceptor() {
		name = "ClusterNotifcationInterceptor";
		priority = 3000;
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug(exc.getOriginalRequestUri());

		Matcher m = pattern.matcher(exc.getOriginalRequestUri());
		
		if (!m.matches()) return Outcome.CONTINUE;
		
		log.debug("request received: "+m.group(1));
		
		if (validateSignature && !verifySignature(getParams(exc)))
			return respond(exc, 403, "Forbidden");
		
		if ("up".equals(m.group(1))) {
			router.getClusterManager().up(getClusterParam(exc),
					getParams(exc).get("host"), getPortParam(exc));			
		} else {
			router.getClusterManager().down(getClusterParam(exc),
					getParams(exc).get("host"), getPortParam(exc));			
		}
		
		return respond(exc, 204, "No Content");
	}

	private boolean verifySignature(Map<String, String> params) throws Exception {
		if (timeout > 0 && System.currentTimeMillis()- Long.parseLong(params.get("time")) > timeout)
			return false;

		Signature sig = Signature.getInstance("SHA1withDSA");
		sig.initVerify(getCertificate());
		sig.update(getSignedData(params));
		return sig.verify(getSignature(params));
	}

	private byte[] getSignature(Map<String, String> params) throws Exception {
		return Base64.decodeBase64(params.get("signature").getBytes("UTF-8")) ;
	}

	private byte[] getSignedData(Map<String, String> params) throws Exception {
		return (params.get("time")+params.get("cluster")+params.get("host")+params.get("port")).getBytes("UTF-8");
	}

	private Certificate getCertificate() throws Exception {

		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream(FileUtil.prefixMembraneHomeIfNeeded(new File(keyStore))), 
				storePass.toCharArray());

		return ((KeyStore.PrivateKeyEntry)ks.getEntry(keyAlias, new KeyStore.PasswordProtection(keyPass.toCharArray()))).getCertificate();
	}

	private int getPortParam(Exchange exc) throws Exception {
		return Integer.parseInt(getParams(exc).get("port"));
	}

	private String getClusterParam(Exchange exc) throws Exception {
		return getParams(exc).get("cluster") == null ? "Default" : getParams(
				exc).get("cluster");
	}

	private Map<String, String> getParams(Exchange exc) throws Exception {
		String uri = exc.getOriginalRequestUri();
		int qStart = uri.indexOf('?');
		if (qStart == -1 || qStart + 1 == uri.length())
			return new HashMap<String, String>();
		return parseQueryString(exc.getOriginalRequestUri().substring(
				qStart + 1));
	}

	private Outcome respond(Exchange exc, int code, String msg) throws Exception {
		Response res = new Response();
		res.setStatusCode(code);
		res.setStatusMessage(msg);
		res.setHeader(createHeader());

		exc.setResponse(res);
		return Outcome.ABORT;
	}

	private Header createHeader() {
		Header header = new Header();
		header.setContentType("text/html;charset=utf-8");
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");
		return header;
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

	public String getKeyStore() {
		return keyStore;
	}

	public void setKeyStore(String keyStore) {
		this.keyStore = keyStore;
	}

	public String getKeyPass() {
		return keyPass;
	}

	public void setKeyPass(String keyPass) {
		this.keyPass = keyPass;
	}

	public String getStorePass() {
		return storePass;
	}

	public void setStorePass(String storePass) {
		this.storePass = storePass;
	}

	public String getKeyAlias() {
		return keyAlias;
	}

	public void setKeyAlias(String keyAlias) {
		this.keyAlias = keyAlias;
	}
	
	
}
