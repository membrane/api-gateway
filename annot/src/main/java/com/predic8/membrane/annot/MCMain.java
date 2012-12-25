package com.predic8.membrane.annot;
 
import java.lang.annotation.*;
 
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MCMain {
	String outputPackage();
	String outputName();
	String prefixXSD();
	String postfixXSD();
}