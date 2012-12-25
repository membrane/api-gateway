/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.w3c.dom.Element;

public class AbstractParser extends AbstractSingleBeanDefinitionParser {

	private boolean inlined = false;

	public BeanDefinition parse(Element e) {
		inlined = true;
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(getBeanClass(e));
		doParse(e, builder);
		return builder.getBeanDefinition();
	}
	
	protected void setIdIfNeeded(Element element, String defaultId) {
		if ( !isInlined() && !element.hasAttribute("id") ) element.setAttribute("id", defaultId);
	}

	protected boolean isInlined() {		
		return inlined ;
	}	

	protected void setProperty(String prop, Element e, BeanDefinitionBuilder builder) {
		builder.addPropertyValue(prop, e.getAttribute(prop));
	}

	protected void setPropertyIfSet(String prop, Element e, BeanDefinitionBuilder builder) {
		if (e.hasAttribute(prop)) {
			builder.addPropertyValue(prop, e.getAttribute(prop));
		}
	}

}
