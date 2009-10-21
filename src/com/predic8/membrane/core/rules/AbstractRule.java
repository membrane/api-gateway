package com.predic8.membrane.core.rules;

import java.util.Collections;
import java.util.List;

import com.predic8.membrane.core.config.XMLElement;
import com.predic8.membrane.core.interceptor.Interceptor;

public abstract class AbstractRule extends XMLElement implements Rule {

	protected String name = "";
	
	protected RuleKey ruleKey;
	
	protected boolean blockRequest;
	protected boolean blockResponse;
	
	public AbstractRule() {
		
	}
	
	public AbstractRule(RuleKey ruleKey) {
		this.ruleKey = ruleKey;
	}
	
	public List<Interceptor> getInInterceptors() {
		return Collections.emptyList();
	}

	public List<Interceptor> getOutInterceptors() {
		return Collections.emptyList();
	}
	
	public String getName() {
		return name;
	}

	public RuleKey getRuleKey() {
		return ruleKey;
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

	public void setRuleKey(RuleKey ruleKey) {
		this.ruleKey = ruleKey;
	}
	
	@Override
	public String toString() {
		if (!"".equals(name))
			return name;
		return "" + getRuleKey().toString();
	}
	
	public void setBlockRequest(boolean blockStatus) {
		
	}
	
	public void setBlockResponse(boolean blockStatus) {
		
	}

}
