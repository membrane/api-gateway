package com.predic8.chat;

import javax.xml.ws.BindingProvider;

import com.predic8.chat.*;

public class Client {
	public static void main(String[] args) {
		ChatService service = new ChatService();
		ChatServicePT port = service.getChatServicePTPort();
		((BindingProvider)port).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, "http://localhost:8080/ChatService");		

		String session = port.login(args[0]);
		System.out.println("login: " + session);

		Say msg = new Say();
		msg.setMsg("Hallo World!");
		for ( int i = 0; i < 10; i++) {
			port.say(msg, session);
			System.out.println("say: "+msg.getMsg());
		}
	}
}
