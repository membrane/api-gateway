package com.predic8.membrane.core.transport.http.client;

import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="authentication", topLevel=false)
public class AuthenticationConfiguration {

	private String username;
	private String password;
	
	public String getUsername() {
		return username;
	}
	
	@Required
	@MCAttribute
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getPassword() {
		return password;
	}
	
	@Required
	@MCAttribute
	public void setPassword(String password) {
		this.password = password;
	}

}
