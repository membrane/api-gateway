package com.predic8.membrane.annot;
 
import java.lang.annotation.*;
 
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MCMain {
	String outputPackage();
	String outputName();
	String targetNamespace();
}