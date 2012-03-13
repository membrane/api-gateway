package com.predic8.membrane.core.multipart;

import java.io.InputStream;

import com.predic8.membrane.core.http.Message;

/**
 * Reassemble a multipart XOP message (see http://en.wikipedia.org/wiki/XML-binary_Optimized_Packaging ) into
 * one stream (that can be used for schema validation, for example).
 */
public class SOAPMessageAccessor {
	
	public InputStream getSOAPStream(Message message) {
		// TODO: if content is XOP, extract parts and reassemble message
		return message.getBodyAsStream();
	}

}
