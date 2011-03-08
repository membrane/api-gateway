package com.predic8.membrane.core.rules;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.core.config.AbstractXMLElement;
import com.predic8.membrane.core.interceptor.Interceptor;

public abstract class AbstractRule extends AbstractXMLElement implements Rule {

	protected String name = "";
	
	protected RuleKey key;
	
	protected boolean blockRequest;
	protected boolean blockResponse;
	
	protected boolean inboundTSL;
	
	protected boolean outboundTSL;
	
	protected List<Interceptor> interceptors = new ArrayList<Interceptor>();
	
	public AbstractRule() {
		
	}
	
	public AbstractRule(RuleKey ruleKey) {
		this.key = ruleKey;
	}
	
	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public String getName() {
		return name;
	}

	public RuleKey getKey() {
		return key;
	}

	public boolean isBlockRequest() {
		return blockRequest;
	}

	public boolean isBlockResponse() {
		return blockResponse;
	}

	public void setName(String name) {
		if (name == null)
			return;
		this.name = name;

	}

	public void setKey(RuleKey ruleKey) {
		this.key = ruleKey;
	}
	
	@Override
	public String toString() {
		if (!"".equals(name))
			return name;
		return "" + getKey().toString();
	}
	
	public void setBlockRequest(boolean blockStatus) {
		this.blockRequest = blockStatus;
	}
	
	public void setBlockResponse(boolean blockStatus) {
		this.blockResponse = blockStatus;
	}

	public boolean isInboundTSL() {
		return inboundTSL;
	}
	
	public boolean isOutboundTLS() {
		return outboundTSL;
	}
	
	public void setInboundTSL(boolean status) {
		inboundTSL = status;	
	}
	
	public void setOutboundTSL(boolean status) {
		this.outboundTSL = status;
	}
}
