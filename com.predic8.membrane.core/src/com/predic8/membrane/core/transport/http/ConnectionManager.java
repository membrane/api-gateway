/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import static com.predic8.membrane.core.util.TextUtil.isNullOrEmpty;

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
		
		List<Connection> closed = new ArrayList<Connection>();
		
		for (Connection cc : connections) {
			if (cc.isClosed()) {
				closed.add(cc);
				continue;
			}
			if (cc.isSame(host, port))
				return cc;
		}
		
		connections.removeAll(closed);
		return createSocket(host, port, localHost, tls);
	}
	
	private Connection createSocket(String host, int port, String localHost, boolean tls) throws UnknownHostException, IOException {
		
		Connection con = new Connection();
		
		if (tls) {
			if (isNullOrEmpty(localHost))
				con.socket = SSLSocketFactory.getDefault().createSocket(host, port);
			else
				con.socket = SSLSocketFactory.getDefault().createSocket(host, port, InetAddress.getByName(localHost), 0);
		} else {
			if (isNullOrEmpty(localHost))
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
	
}
