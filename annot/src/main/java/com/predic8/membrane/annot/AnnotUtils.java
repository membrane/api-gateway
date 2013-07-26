package com.predic8.membrane.annot;

import javax.lang.model.element.TypeElement;

public class AnnotUtils {

	public static String javaify(String s) {
		StringBuilder sb = new StringBuilder(s);
		sb.replace(0, 1, "" + Character.toUpperCase(s.charAt(0)));
		return sb.toString();
	}

	public static String dejavaify(String s) {
		StringBuilder sb = new StringBuilder(s);
		sb.replace(0, 1, "" + Character.toLowerCase(s.charAt(0)));
		return sb.toString();
	}

	public static String getRuntimeClassName(TypeElement element) {
		if (element.getEnclosingElement() instanceof TypeElement) {
			return getRuntimeClassName((TypeElement) element.getEnclosingElement()) + "$" + element.getSimpleName();
		}
		return element.getQualifiedName().toString();
	}

}
