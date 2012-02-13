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
