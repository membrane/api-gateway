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
package com.predic8.membrane.core.http;

import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;

/**
 * A {@link MessageObserver} may be registered on a HTTP {@link Message} and
 * will be called, when the message's body becomes fully known to Membrane.
 * 
 * A typical example of an interceptor using {@link MessageObserver} is the
 * {@link ExchangeStoreInterceptor}, whose primary purpose is to write the
 * message's body into permanent storage. As this requires reading the body,
 * directly "streaming" the body from the HTTP client to the HTTP server (as
 * described in {@link AbstractBody}) would not be possible. Instead,
 * {@link ExchangeStoreInterceptor} registers a {@link MessageObserver} on the
 * message. This {@link MessageObserver} will be called back once streaming has
 * been completed and the messages' body is fully known. The
 * {@link MessageObserver} will the write the body into permanent storage.
 */
public interface MessageObserver {
	public void bodyRequested(AbstractBody body);

	/**
	 * Notification that the body has fully been received.
	 * 
	 * Note that this event may run instantaneously (during the call to
	 * {@link Message#addObserver(MessageObserver)}), as the body may have
	 * already been fully received when registering the observer.
	 * 
	 * This is the last event that will be fired on any MessageObserver.
	 */
	public void bodyComplete(AbstractBody body);
	
}
