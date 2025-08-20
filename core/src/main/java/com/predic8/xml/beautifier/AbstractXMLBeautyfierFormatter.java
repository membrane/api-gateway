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

public abstract class AbstractXMLBeautyfierFormatter implements XMLBeautifierFormatter {

	protected Writer writer;

	protected int indent;

	public AbstractXMLBeautyfierFormatter(Writer writer, int indent) {
		setWriter(writer);
		setIndent(indent);
	}

	public void indent() throws IOException {
		for (int i = 0; i < indent; i++) {
			writer.write(" ");
		}
	}

	public void setIndent(int indent) {
		this.indent = indent;
	}

	public void setWriter(Writer writer) {
		if (writer == null) {
			throw new IllegalArgumentException("Writer can not be null.");
		}
		this.writer = writer;
	}

	public void incrementIndentBy(int value) {
		indent += value;
	}

	public void decrementIndentBy(int value) {
		indent -= value;
	}

	public void writeText(String text) throws IOException {
		writer.write(text);
	}
	
	public void closeEmptyTag()  throws IOException {
		writer.write(" />");
	}

	public void closeTag() throws IOException {
		writer.write(">");
	}	
}
