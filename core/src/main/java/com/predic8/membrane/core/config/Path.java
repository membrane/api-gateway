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

package com.predic8.membrane.core.config;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;

/**
 * @explanation <p>
 *              The value of the content will be taken to match the path.
 *              </p>
 * 
 *              <p>
 *              If <tt>isRegExp="true"</tt>, the whole path has to match the given regular expression. If
 *              <tt>isRegExp="false"</tt>, the path has to start with the given string.
 *              </p>
 * 
 *              <p>
 *              If <tt>&lt;path /&gt;</tt> is a child element of a <tt>&lt;soapProxy /&gt;</tt>, the <tt>isRegExp</tt>
 *              attribute must not be present.
 *              </p>
 */
@MCElement(name="path", topLevel=false, mixed=true)
public class Path {

	public static final String ELEMENT_NAME = "path";
	
	private String value;
	
	private boolean regExp;
	
	public Path() {
	}
	
	public Path(boolean regExp, String value) {
		this.regExp = regExp;
		this.value = value;
	}
	
	public boolean isRegExp() {
		return regExp;
	}

	/**
	 * @description If set to true the content will be evaluated as a <a href="http://docs.oracle.com/javase/1.4.2/docs/api/java/util/regex/Pattern.html">Java Regular Expression</a>.
	 * @default false
	 * @example true
	 */
	@MCAttribute(attributeName="isRegExp")
	public void setRegExp(boolean regExp) {
		this.regExp = regExp;
	}

	public String getValue() {
		return value;
	}

	@MCTextContent
	public void setValue(String value) {
		this.value = value;
	}
	
}
