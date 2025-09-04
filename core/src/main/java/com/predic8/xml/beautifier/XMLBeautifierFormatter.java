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

public interface XMLBeautifierFormatter {

	void setIndent(int indent);
	
	void setWriter(Writer writer);
	
	void indent() throws IOException;
	
	void closeTag() throws IOException;
	
	void startTag() throws IOException;
	
	void closeEmptyTag() throws IOException;
	
	void incrementIndentBy(int value);
	
	void decrementIndentBy(int value);
	
	void writeNamespaceAttribute(String prefix, String nsUri) throws IOException;
	
	void writeComment(String text) throws IOException;
	
	void printNewLine() throws IOException;
	
	void writeVersionAndEncoding(String version, String encoding) throws IOException;
	
	void writeAttribute(String prefix, String localName, String value) throws IOException;
	
	void writeText(String text) throws IOException;
	
	void writeTag(String prefix, String localName) throws IOException;
	
	void closeTag(String prefix, String localName) throws IOException;
	
}
