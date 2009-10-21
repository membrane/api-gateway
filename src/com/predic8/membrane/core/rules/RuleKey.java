package com.predic8.membrane.core.rules;

public interface RuleKey {

	public int getPort();
	
	public String getMethod();
	
	public String getPath();
	
	public String getHost();
	
	public boolean isMethodWildcard();
	
	public boolean isHostWildcard();
	
	
}
