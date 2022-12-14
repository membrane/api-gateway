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

package com.predic8.chat;

import java.util.Hashtable;
import java.util.concurrent.*;

import jakarta.jws.*;
import jakarta.xml.ws.Endpoint;

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
