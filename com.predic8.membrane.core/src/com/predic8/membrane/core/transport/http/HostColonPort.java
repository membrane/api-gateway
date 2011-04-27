package com.predic8.membrane.core.transport.http;

public class HostColonPort {

	public String host;
	
	public int port;
	
	public HostColonPort(String hostAndPort) {
		String[] strs = hostAndPort.split(":");
		
		if (strs.length < 2)
			throw new IllegalArgumentException("Illegal format");
		
		host = strs[0];
		port = Integer.parseInt(strs[1]);
	}
}
