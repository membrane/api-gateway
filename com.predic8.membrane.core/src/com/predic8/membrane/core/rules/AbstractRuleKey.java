package com.predic8.membrane.core.rules;


public abstract class AbstractRuleKey implements RuleKey {

	int port;
	
	public AbstractRuleKey(int port) {
		this.port = port;
	}
	
	public String getHost() {
		return "";
	}

	public String getMethod() {
		return "";
	}

	public String getPath() {
		return "";
	}

	public int getPort() {
		return port;
	}

	public boolean isHostWildcard() {
		
		return false;
	}

	public boolean isMethodWildcard() {
		
		return false;
	}
	
	public void setPort(int port) {
		this.port = port;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + port;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractRuleKey other = (AbstractRuleKey) obj;
		if (port != other.port)
			return false;
		return true;
	}

	
}
