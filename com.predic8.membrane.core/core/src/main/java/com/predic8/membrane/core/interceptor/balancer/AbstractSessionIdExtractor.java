package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.http.Message;

public abstract class AbstractSessionIdExtractor extends AbstractXmlElement {

	public boolean hasSessionId(Message msg) throws Exception {		
		return getSessionId(msg) != null;
	}

	public abstract String getSessionId(Message msg) throws Exception;
}