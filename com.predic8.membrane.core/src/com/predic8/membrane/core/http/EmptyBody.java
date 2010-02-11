package com.predic8.membrane.core.http;

public class EmptyBody extends Body {

	public EmptyBody() {
		
	}
	
	@Override
	public int getLength() {
		return 0;
	}
	
	@Override
	public byte[] getContent() {
		return new byte[0];
	}
	
}
