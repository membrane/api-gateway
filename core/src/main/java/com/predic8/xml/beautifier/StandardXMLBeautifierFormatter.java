/* Copyright 2008-2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.xml.beautifier;

import java.io.*;

public class StandardXMLBeautifierFormatter extends AbstractXMLBeautyfierFormatter {

	public static final char DOUBLE_QUOTE = '"';

	public StandardXMLBeautifierFormatter(Writer writer, int indent) {
		super(writer, indent);
	}
	
	public void writeTag(String prefix, String localName) throws IOException {
		if (localName == null) {
			return;
		}
		if (prefix != null && !prefix.isEmpty()) {
			writer.write(prefix + ":" + localName);
		} else {
			writer.write(localName);
		}
	}

	@Override
	public void writeNamespaceAttribute(String prefix, String nsUri) throws IOException {
		if (nsUri == null) return;
		String v = escapeAttr(nsUri); // be safe for '&' etc.
		if (prefix != null && !prefix.isEmpty()) {
			writer.write(" xmlns:" + prefix + "=\"" + v + "\"");
		} else {
			writer.write(" xmlns=\"" + v + "\"");
		}
	}

	@Override
	public void writeAttribute(String prefix, String localName, String value) throws IOException {
		if (localName == null || value == null) return;
		String v = escapeAttr(value);
		if (prefix != null && !prefix.isEmpty()) {
			writer.write(prefix + ":" + localName + "=\"" + v + "\"");
		} else {
			writer.write(localName + "=\"" + v + "\"");
		}
	}
	
	public void writeComment(String text) throws IOException {
		if (text == null) {
			return;
		}
		writer.write("<!--");
		writer.write(text);
		writer.write("-->");
	}
	
	public void printNewLine() throws IOException {
		writer.write("\n"); // LF is canonical form of XML
	}
	
	public void startTag()  throws IOException {
		writer.write("<");
	}
	
	public void writeVersionAndEncoding(String version, String encoding) throws IOException {
		if (version == null) {
			return;
		}
		writer.write("<?xml version=\"");
		writer.write( version);
		writer.write(DOUBLE_QUOTE);
		if (encoding != null) {
			writer.write(" encoding=");
			writer.write(DOUBLE_QUOTE);
			writer.write(encoding);
			writer.write(DOUBLE_QUOTE);
		}
		writer.write( "?>");
		printNewLine();
	}
	
	public void closeTag(String prefix, String localName) throws IOException {
		writer.write("</");
		writeTag(prefix, localName);
		writer.write(">");
	}

	@Override
	public void writeText(String text) throws IOException {
		writer.write(escapeText(text));
	}

	private static String escapeText(String s) {
		if (s == null) return null;

		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '&' -> b.append("&amp;");
				case '<' -> b.append("&lt;");
				default  -> b.append(c);
			}
		}
		return b.toString();
	}

	private static String escapeAttr(String s) {
		if (s == null) return null;
		StringBuilder b = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '&' -> b.append("&amp;");
				case '<' -> b.append("&lt;");
				case '>' -> b.append("&gt;");
				case DOUBLE_QUOTE -> b.append("&quot;");
				case '\''-> b.append("&apos;");
				default  -> b.append(c);
			}
		}
		return b.toString();
	}
}
