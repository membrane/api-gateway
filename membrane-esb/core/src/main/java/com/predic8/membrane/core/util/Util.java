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

package com.predic8.membrane.core.util;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

public class Util {
	
	public static void shutdownOutput(Socket socket) throws IOException {
		if (!(socket instanceof SSLSocket) &&
				!socket.isClosed() &&
				!socket.isOutputShutdown()) {
			socket.shutdownOutput();
		}
	}

	public static void shutdownInput(Socket socket) throws IOException {
		//SSLSocket does not implement shutdown input and output
		if (!(socket instanceof SSLSocket) && 
				!socket.isClosed() && 
				!socket.isInputShutdown()) {
			socket.shutdownInput();
		}
	}

}
