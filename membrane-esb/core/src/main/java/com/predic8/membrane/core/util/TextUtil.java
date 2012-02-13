/* Copyright 2009 predic8 GmbH, www.predic8.com

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



import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import com.predic8.beautifier.PlainBeautifierFormatter;
import com.predic8.beautifier.XMLBeautifier;
import com.predic8.beautifier.XMLBeautifierFormatter;


public class TextUtil {

	private static final char[] source;
	private static final String[] replace;
	
	static { 
		source = new char[] {    '*',    '?',  '.',    '\\',      '(' ,    ')',    '+',      '|',    '^',     '$',    '%',       '@'    };
		replace = new String[] { ".*",   ".",  "\\.",  "\\\\",   "\\(",   "\\)",   "\\+",   "\\|",  "\\^",   "\\$",    "\\%",   "\\@"   };
	}
	
	public static String formatXML(Reader reader) {
		StringWriter out = new StringWriter();
		
		try {      
			XMLBeautifierFormatter formatter = new PlainBeautifierFormatter(out, 0);
			XMLBeautifier beautifier = new XMLBeautifier(formatter);
			beautifier.parse(reader);
		}
		catch (Exception e){
			e.printStackTrace();
		} finally {
			try {
				out.close();
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return out.toString();
		
	}

	public static boolean isNullOrEmpty(String str) {
		return str == null || str.length() == 0;
	}
	
	public static String globToRegExp(String glob) {
		StringBuffer buf = new StringBuffer();
		buf.append("^");
		for(int i = 0; i < glob.length(); i ++) {
			appendReplacement(glob.charAt(i), buf);
		}
		buf.append("$");
		return buf.toString();
	}
	
	private static void appendReplacement(char c, StringBuffer buf) {
		for (int j = 0; j < source.length; j++) {
			if (c == source[j]) {
				buf.append(replace[j]);
				return;
			} 
		}
		buf.append(c);
	}
}