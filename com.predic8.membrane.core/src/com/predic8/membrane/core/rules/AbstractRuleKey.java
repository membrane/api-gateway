package com.predic8.membrane.core.rules;

import java.util.regex.Pattern;


public abstract class AbstractRuleKey implements RuleKey {

	protected int port;
	
	private String path;
	
	protected Pattern pathPattern;
	
	protected boolean pathRegExp = true;
	
	protected boolean usePathPattern; 
	
	public AbstractRuleKey(int port) {
		this.port = port;
	}
	
	public String getHost() {
		return "";
	}

	public String getMethod() {
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

	public boolean isPathRegExp() {
		return pathRegExp;
	}

	public void setPathRegExp(boolean pathRegExp) {
		this.pathRegExp = pathRegExp;
	}

	public boolean isUsePathPattern() {
		return usePathPattern;
	}

	public void setUsePathPattern(boolean usePathPattern) {
		this.usePathPattern = usePathPattern;
	}
	
	public void setPath(String path) {
		this.path = path;
	}
	
	public String getPath() {
		return path;
	}
	
	public Pattern getPathPattern() {
		if (pathPattern == null)
			pathPattern = Pattern.compile(path);
		
		return pathPattern;
	}
	
}
