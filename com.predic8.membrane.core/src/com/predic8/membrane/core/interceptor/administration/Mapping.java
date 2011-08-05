package com.predic8.membrane.core.interceptor.administration;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Mapping {
	String value();
}
