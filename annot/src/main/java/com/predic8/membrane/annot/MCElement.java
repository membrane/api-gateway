package com.predic8.membrane.annot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MCElement {
	String name();
	String group() default "interceptor";
	boolean mixed() default false;
	String xsd() default "";
	boolean global() default true;
}
