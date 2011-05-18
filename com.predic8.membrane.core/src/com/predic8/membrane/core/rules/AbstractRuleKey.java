/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
	
	public boolean matchesPath(String path) {
		if (isPathRegExp())
			return matchesPathPattern(path);
		
		return path.indexOf(getPath()) >=0;
	}
	
	private boolean matchesPathPattern(String path) {
		return getPathPattern().matcher(path).matches();
	}
	
	private Pattern getPathPattern() {
		if (pathPattern == null)
			pathPattern = Pattern.compile(path);
		
		return pathPattern;
	}
}
