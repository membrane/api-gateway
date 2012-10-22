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

package com.predic8.membrane.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.predic8.membrane.core.interceptor.AbstractInterceptor;

/**
 * Used to extend the namespace of proxies.xml with classes which do not belong to the core project.
 * 
 * Declare a class
 * <code>@ElementName("my") public class MyInterceptor extends AbstractInterceptor {</code>
 * and define a bean
 * <pre>&lt;spring:bean id="my" class="MyInterceptor" /&gt;</pre>.
 * 
 * Then an element <pre>&lt;my/&gt;</pre> will be available for usage in the proxies.xml .
 * 
 * The parsing of the content of <pre>&lt;my/&gt;</pre> has to be implemented in 
 * {@link AbstractInterceptor#parse(javax.xml.stream.XMLStreamReader)}.
 * 
 * The instance created by Spring by the bean declaration above is not used for anything.
 * 
 * The bean class has to have a default constructor. It will be used by spring for the useless bean
 * as well as by AbstractProxy#getInlinedInterceptor to create more instances, which will actually
 * be used. 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ElementName {
	String value();
}
