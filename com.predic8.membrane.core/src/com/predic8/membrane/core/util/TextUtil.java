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
import java.io.InputStream;
import java.io.StringWriter;

import com.predic8.beautifier.PlainBeautifierFormatter;
import com.predic8.beautifier.XMLBeautifier;
import com.predic8.beautifier.XMLBeautifierFormatter;


public class TextUtil {

	public static String formatXML(InputStream src) {
		StringWriter out = new StringWriter();
		
		try {      
			XMLBeautifierFormatter formatter = new PlainBeautifierFormatter(out, 0);
			XMLBeautifier beautifier = new XMLBeautifier(formatter);
			beautifier.parse(src);
		}
		catch (Exception e){
			e.printStackTrace();
		} finally {
			try {
				out.close();
				src.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return out.toString();
		
	}

}