package com.predic8.membrane.core.transport.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Connection {
	
	private static Log log = LogFactory.getLog(Connection.class.getName());
	
	public Socket socket;
	public InputStream in;
	public OutputStream out;

	public boolean isSame(String host, int port) {
		return (host.equalsIgnoreCase(socket.getInetAddress().getHostName()) || host.equals(socket.getInetAddress().getHostAddress())) && port == socket.getPort();
	}

	public void close() throws IOException {
		if (isClosed())
			return;
		
		log.debug("Closing HTTP connection LocalPort: " + socket.getLocalPort());
		
		if (in != null)
			in.close();
	
		if (out != null) {
			out.flush();
			out.close();
		}
	
		// Test for isClosed() is needed!
		if (!(socket instanceof SSLSocket) && !socket.isClosed())
			socket.shutdownInput();
		
		socket.close();
	}

	public boolean isClosed() {
		return socket == null || socket.isClosed();
	}
}