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
 * Observes one {@link AbstractBody} while Membrane consumes it.
 * <p>
 * A typical example is {@link ExchangeStoreInterceptor}, whose purpose is to write the message's body
 * into permanent storage. As that requires reading the body, streaming it straight from the HTTP
 * client to the HTTP server (as described in {@link AbstractBody}) would not be possible. Instead it
 * registers an observer, is called back as the body is consumed, and writes the body away.
 *
 * <h2>Lifecycle</h2>
 * An observer receives {@link #bodyRequested(AbstractBody)} once, then zero or more
 * {@link #bodyChunk} calls, then <i>exactly one</i> terminal event: either
 * {@link #bodyComplete(AbstractBody)} or {@link #bodyFailed(ReadingBodyException)}. The two are
 * mutually exclusive - a body that failed never becomes complete.
 * <p>
 * The terminal event is final in a strong sense: the body drops its observers when it fires one, so
 * nothing reaches the observer afterwards, whatever happens to the body or the message later. An
 * observer that holds a resource for the duration of the body - an open stream, a pooled connection,
 * a buffer - can therefore release it there and nowhere else.
 * <p>
 * A terminal event may fire <i>during</i> {@link AbstractBody#addObserver(MessageObserver)} rather
 * than later, because the body may already be complete, or already have failed, when the observer is
 * registered.
 *
 * <h2>What an observer observes</h2>
 * An observer is registered on one body and observes that body only. It is a snapshot taken at the
 * point in the interceptor chain where it was added.
 * <p>
 * If a later interceptor replaces the message's body, the replacement is a different
 * {@link AbstractBody} with its own observers. The earlier observer is not carried over to it and
 * will never see it - by then it has had its terminal event for the body it was watching. So an
 * observer cannot be used to watch "the body of this message" across the whole chain; it watches the
 * body that was there when it was added.
 *
 * <h2>Streaming</h2>
 * Observers are the reason a body may have to be held in memory: if any observer needs the bytes, the
 * body cannot be streamed straight through. An observer that never looks at the content should
 * therefore also implement {@link NonRelevantBodyObserver}, which marks it as not needing the chunks
 * and lets Membrane keep streaming. Such an observer still receives {@link #bodyRequested} and the
 * terminal event; only the {@link #bodyChunk} calls are skipped.
 *
 * <h2>Threading</h2>
 * Callbacks run on the thread consuming the body, and a body must not be accessed from more than one
 * thread (see {@link AbstractBody}). An observer therefore needs no synchronization of its own for
 * the sequence above, but it must not block: it runs inside body reading and delays the exchange.
 */
public interface MessageObserver {

	/**
	 * The body is about to be consumed. Always the first event, and fired even when no chunk follows.
	 */
	void bodyRequested(AbstractBody body);

	/**
	 * Observes a piece of the body.
	 * <p>
	 * Not fired for observers marked {@link NonRelevantBodyObserver}.
	 */
	void bodyChunk(Chunk chunk);

	/**
	 * Observes a piece of the body. Note that the buffer content may change after the method completes,
	 * so an observer that keeps the bytes has to copy them.
	 * <p>
	 * Not fired for observers marked {@link NonRelevantBodyObserver}.
	 */
	void bodyChunk(byte[] buffer, int offset, int length);

	/**
	 * Terminal event: the body has been received in full, and the observer has seen all of it.
	 * <p>
	 * Note that this event may run instantaneously (during the call to
	 * {@link AbstractBody#addObserver(MessageObserver)}), as the body may have already been fully
	 * received when the observer is registered.
	 * <p>
	 * Mutually exclusive with {@link #bodyFailed(ReadingBodyException)}; see the lifecycle above.
	 */
	void bodyComplete(AbstractBody body);

	/**
	 * Terminal event: the body will not be delivered in full, so no {@link #bodyComplete(AbstractBody)}
	 * will follow. Today the only cause is a failure while reading it.
	 * <p>
	 * Like {@link #bodyComplete(AbstractBody)} this may run instantaneously during
	 * {@link AbstractBody#addObserver(MessageObserver)}, when the body has already failed.
	 * <p>
	 * Defaults to doing nothing: only observers that hold a resource for the duration of the body (an
	 * open stream, a pooled connection, a buffer) need to act on it.
	 *
	 * @param e the recorded failure
	 */
	default void bodyFailed(ReadingBodyException e) {
		// most observers have nothing to release
	}

}
