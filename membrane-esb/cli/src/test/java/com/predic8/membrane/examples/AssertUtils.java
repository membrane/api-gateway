package com.predic8.membrane.examples;

public class AssertUtils {
	
	public static void assertContains(String needle, String haystack) {
		if (haystack.contains(needle))
			return;
		throw new AssertionError("The string '" + haystack + "' does not contain '" + needle + "'.");
	}

	public static void assertContainsNot(String needle, String haystack) {
		if (!haystack.contains(needle))
			return;
		throw new AssertionError("The string '" + haystack + "' does contain '" + needle + "'.");
	}

}
