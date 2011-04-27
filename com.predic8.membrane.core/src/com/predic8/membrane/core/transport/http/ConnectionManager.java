package com.predic8.membrane.core.transport.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ConnectionManager {

	private static Log log = LogFactory.getLog(ConnectionManager.class.getName());
	
	List<Connection> connections = new ArrayList<Connection>();

	public Connection getConnection(String host, int port, String localHost, boolean tls) throws UnknownHostException, IOException {
		
		log.debug("connection requested for host: " + host + " and port: " + port);
		
		log.debug("Number of connections in pool: " + connections.size());
		
		for (Connection cc : connections) {
			if (cc.isClosed())
				continue;
				
			if (cc.isSame(host, port))
				return cc;
		}
		
		return createSocket(host, port, localHost, tls);
	}
	
	private Connection createSocket(String host, int port, String localHost, boolean tls) throws UnknownHostException, IOException {
		
		Connection con = new Connection();
		
		if (tls) {
			if (localHost == null || localHost.equals(""))
				con.socket = SSLSocketFactory.getDefault().createSocket(host, port);
			else
				con.socket = SSLSocketFactory.getDefault().createSocket(host, port, InetAddress.getByName(localHost), 0);
		} else {
			if (localHost == null || localHost.equals(""))
				con.socket = new Socket(host, port);
			else
				con.socket = new Socket(host, port, InetAddress.getByName(localHost), 0);
		}
		
		log.debug("Opened connection on localPort: " + port);
		con.in = new BufferedInputStream(con.socket.getInputStream(), 2048);
		con.out = new BufferedOutputStream(con.socket.getOutputStream(), 2048);
		
		connections.add(con);
		
		return con;
	}
	
	
	/*
	 * 
	 * private void openSocketIfNeeded() throws UnknownHostException, IOException {

		while (con.isClosed() || !con.isSame(host, port)) {

			con.close();

			log.debug("opening a new socket for host: " + host + " on port: " + port);
			createSocket();
			

			log.debug("Opened connection on localPort: " + port);
			con.in = new BufferedInputStream(con.socket.getInputStream(), 2048);
			con.out = new BufferedOutputStream(con.socket.getOutputStream(), 2048);
		}

	}
	 * 
	 * 
	 * 
	 */
	
}
