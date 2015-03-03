/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.util;

import com.predic8.membrane.examples.AbstractConsoleWatcher;

public class BufferLogger extends AbstractConsoleWatcher {

	private StringBuffer b = new StringBuffer();
	private String newline = System.getProperty("line.separator");
	
	@Override
	public void outputLine(boolean error, String line) {
		synchronized(b) {
			b.append(line);
			b.append(newline);
		}
	}
	
	@Override
	public String toString() {
		synchronized(b) {
			return b.toString();
		}
	}
}
