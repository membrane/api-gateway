package com.predic8.membrane.core.interceptor.rest;

import org.codehaus.jackson.JsonGenerator;

public interface JSONContent {
	void write(JsonGenerator jsonGen) throws Exception;
}
