/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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


public interface RuleKey {

	/**
	 * Returns the TCP port that receives the messages
	 * @return 
	 * 		an integer value which corresponds to TCP port
	 * 		
	 */
	public int getPort();
	
	/**
	 * Returns the name of the HTTP method
	 * @return 
	 * 		a String specifying the name of the HTTP method
	 */
	public String getMethod();
	
	/**
	 * Returns the request URI. 
	 * For the request 
	 * 		GET index.htm
	 * it is
	 * 		index.htm
	 * 
	 * @return 
	 * 		a String specifying the request URI
	 */
	public String getPath();
	
	/**
	 * @return the value of the HTTP Host header field
	 */
	public String getHost();
	
	/**
	 * When isMethodWildcard is set to true any value of the HTTP Host header will match
	 * @return  true
	 * 				if HTTP method wildcard is set to true
	 * 			false
	 * 				otherwise
	 */			
	 
	public boolean isMethodWildcard();
	
	/**
	 * When isHostWidldcard is set to true any value of the HTTP Host header will match.
	 * @return whether the host wildcard is set to true
	 */
	public boolean isHostWildcard();
	
	/**
	 * If isPathRegExp is true, than the path will be treated as a regexp pattern. 
	 * The path of the incoming request will be match against this regexp. 
	 * 
	 * return true
	 * 			if a path is an regexp expression
	 * 		  false
	 * 			otherwise
	 */
	public boolean isPathRegExp();
	
	public boolean isUsePathPattern();
	
	public void setUsePathPattern(boolean usePathPattern);
	
	public void setPathRegExp(boolean pathRegExp);
	
	public void setPath(String path);
	
	public boolean matchesPath(String path); 
	
	/**
	 * IP address to bind to, or null to bind to 0.0.0.0 (=any local address).
	 */
	public String getIp();
	
	public void setIp(String ip);
}
