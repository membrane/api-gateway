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

package com.predic8.membrane.core.util;

import com.predic8.xml.beautifier.*;
import org.apache.commons.text.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.util.*;

import static javax.xml.stream.XMLInputFactory.*;


public class TextUtil {
	private static final Logger log = LoggerFactory.getLogger(TextUtil.class.getName());

	// Guess for a very short XML
	private static final int STRING_BUFFER_INITIAL_CAPACITY_FOR_XML = 250;

	// TODO make it thread safe! See
	private static final XMLInputFactory xmlInputFactory = newInstance();

	private static final char[] source;
	private static final String[] replace;

	static {
		source = new char[] {    '*',    '?',  '.',    '\\',      '(' ,    ')',    '+',      '|',    '^',     '$',    '%',       '@'    };
		replace = new String[] { ".*",   ".",  "\\.",  "\\\\",   "\\(",   "\\)",   "\\+",   "\\|",  "\\^",   "\\$",    "\\%",   "\\@"   };
	}

	public static String camelToKebab(String string) {
		// lower/digit to uppercase
		String kebab = string.replaceAll("([a-z0-9])([A-Z])", "$1-$2");
		// uppercase followed by uppercase to lowercase
		kebab = kebab.replaceAll("([A-Z])([A-Z][a-z])", "$1-$2");
		return kebab.toLowerCase();
	}

	/**
	 *
	 * @param reader
	 * @return
	 * @throws Exception
	 */
	public static String formatXML(Reader reader) throws Exception {
		return formatXML(reader, false);
	}

	public static String formalXML(InputStream inputStream) throws Exception {
		try(InputStream is = inputStream) {
			StringWriter out = new StringWriter(STRING_BUFFER_INITIAL_CAPACITY_FOR_XML);
            new XMLBeautifier(new StandardXMLBeautifierFormatter(out, 4)).parse(is);
			return out.toString();
		} catch (XMLStreamException e) {
			log.info("Error parsing XML: {}", e.getMessage());
			throw e;
		}
	}

	/**
	 * As HTML is needed for the AdminConsole
	 * @param reader XML
	 * @param asHTML Should output formatted as XML
	 * @return Formatted string
	 * @throws Exception
	 */
	public static String formatXML(Reader reader, boolean asHTML) throws Exception {
		try(Reader r = reader) {
			StringWriter out = new StringWriter(STRING_BUFFER_INITIAL_CAPACITY_FOR_XML);
            new XMLBeautifier(getXmlBeautifierFormatter(asHTML, out)).parse(r);
			return out.toString();
		}
		catch (Exception e){
			log.info("Error parsing XML: {}", e.getMessage());
			throw e;
		}
	}

	private static @NotNull XMLBeautifierFormatter getXmlBeautifierFormatter(boolean asHTML, StringWriter out) {
		return asHTML ? new HtmlBeautifierFormatter(out, 0) : new StandardXMLBeautifierFormatter(out, 4);
	}

	public static boolean isNullOrEmpty(String str) {
		return str == null || str.isEmpty();
	}

	public static String globToRegExp(String glob) {
		StringBuilder buf = new StringBuilder();
		buf.append("^");
		for(int i = 0; i < glob.length(); i ++) {
			appendReplacement(glob.charAt(i), buf);
		}
		buf.append("$");
		return buf.toString();
	}

	private static void appendReplacement(char c, StringBuilder buf) {
		for (int j = 0; j < source.length; j++) {
			if (c == source[j]) {
				buf.append(replace[j]);
				return;
			}
		}
		buf.append(c);
	}

	public static String toEnglishList(String conjuction, String... args) {
		ArrayList<String> l = new ArrayList<>();
		for (String arg : args)
			if (arg != null && !arg.isEmpty())
				l.add(arg);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < l.size(); i++) {
			sb.append(l.get(i));
			if (i == l.size() - 2) {
				sb.append(" ");
				sb.append(conjuction);
				sb.append(" ");
			}
			if (i < l.size() - 2)
				sb.append(", ");
		}
		return sb.toString();
	}

	public static Object capitalize(String english) {
		if (english.isEmpty())
			return "";
		return (english.charAt(0) + english.substring(1)).toUpperCase();
	}



	static {
		xmlInputFactory.setProperty(IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	/**
	 * Checks whether s is a valid (well-formed and balanced) XML snippet.
	 * Note that attributes escaped by single quotes are accepted (which is illegal by spec).
	 */
	public static boolean isValidXMLSnippet(String s) {
		try {
			XMLEventReader parser;
			synchronized (xmlInputFactory) {
				parser = xmlInputFactory.createXMLEventReader(new StringReader("<a>" + s + "</a>"));
			}
			XMLEvent event = null;
			while (parser.hasNext()) {
				event = (XMLEvent) parser.next();
			}
			return event != null && event.isEndDocument();
		} catch (Exception e) {
			log.error("", e);
			return false;
		}
	}

	public static String linkURL(String url) {
		if (url.startsWith("http://") || url.startsWith("https://")) {
			url = StringEscapeUtils.escapeHtml4(url);
			return "<a href=\"" + url + "\">" + url + "</a>";
		}
		return StringEscapeUtils.escapeHtml4(url);
	}

	public static Object removeFinalChar(String s) {
		StringBuilder sb = new StringBuilder(s);
		if (!sb.isEmpty())
			sb.deleteCharAt(sb.length()-1);
		return sb.toString();
	}

	/**
	 * Counts from 1 cause this is needed for getting lines from Javascript source code.
	 *
	 * @param s Multiline string
	 * @param lineNumber number of line to return. Counts from 1
	 * @return line
	 */
	public static String getLineFromMultilineString(String s,int lineNumber) {
		return s.split("\n")[lineNumber-1];
	}

	public static String escapeQuotes(String s) {
		return s.replace("\"", "\\\"");
	}

	/**
	 * Adjusts the indentation of each line in a multiline string to match the minimum indentation found.
	 *
	 * @param multilineString The input multiline string to process.
	 * @return A string with adjusted indentation.
	 */
	public static String unifyIndent(String multilineString) {
		String[] lines = multilineString.split("\r?\n");
		return trimLines(lines, getMinIndent(lines)).toString().replaceFirst("\\s*$", "");
	}

	/**
	 * Trims excess indentation from each line in the input array, based on a specified minimum indent level.
	 *
	 * @param lines The array of lines to process.
	 * @param minIndent The minimum indent level to maintain.
	 * @return A StringBuilder containing lines with adjusted indentation.
	 */
	public static StringBuilder trimLines(String[] lines, int minIndent) {
		StringBuilder result = new StringBuilder();
		for (String line : lines) {
			if (!line.trim().isEmpty()) {
				result.append(" ".repeat(Math.max(getCurrentIndent(line) - minIndent, 0))).append(line.trim()).append("\n");
			} else {
				result.append("\n");
			}
		}
		return result;
	}

	/**
	 * Calculates the current indentation level (number of leading spaces) of a given line.
	 *
	 * @param line The line to calculate the indentation for.
	 * @return The number of leading spaces in the line.
	 */
	public static int getCurrentIndent(String line) {
		return line.length() - line.replaceFirst("^\\s+", "").length();
	}

	/**
	 * Determines the minimum indentation level (number of leading spaces) across all non-empty lines in an array of lines.
	 *
	 * @param lines The array of lines to analyze.
	 * @return The minimum indent level found among the lines.
	 */
	public static int getMinIndent(String[] lines) {
		int minIndent = Integer.MAX_VALUE;
		for (String line : lines) {
			if (!line.trim().isEmpty()) {
				int leadingSpaces = getCurrentIndent(line);
				minIndent = Math.min(minIndent, leadingSpaces);
			}
		}
		return minIndent;
	}

}
