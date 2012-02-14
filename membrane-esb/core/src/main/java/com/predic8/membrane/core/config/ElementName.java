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
