package com.predic8.chat;

import java.util.Hashtable;
import java.util.concurrent.*;

import javax.jws.*;
import javax.xml.ws.Endpoint;

import com.predic8.chat.*;

@WebService(endpointInterface="com.predic8.chat.ChatServicePT")
public class Server implements ChatServicePT {

	private String nodeName;
	private int id = 0;
	private Hashtable<String, String> sessions = new Hashtable<String, String>();
	
	public Server(String name) {
		this.nodeName = name;
	}
	

	public static void main(String[] args) {
		Endpoint ep = Endpoint.create(new Server(args[0]));
		ep.setExecutor(Executors.newFixedThreadPool(20));
		String epAddress = "http://localhost:"+args[1]+"/";
		ep.publish(epAddress);
		System.out.println(args[0] + " published at " + epAddress);
	}


	@Override
	public String login(String userName) {
		String session = nodeName+"-"+(++id);
		sessions.put(session, userName);
		System.out.println(userName +" logged in.");
		return session;
	}


	@Override
	public SayResponse say(Say msg, String session) {
		System.out.println(sessions.get(session)+" says: "+msg.getMsg());
		return new SayResponse();
	}
	
}
