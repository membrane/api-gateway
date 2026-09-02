/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.BodyState.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An HTTP message body (request or response), as it is received or constructed
 * internally by Membrane.
 * <p>
 * (Sending a body is handled by one of the {@link AbstractBodyTransferer}s.)
 * <p>
 * To read a body, use the concrete implementation {@link ChunkedBody} (iff
 * "Transfer-Encoding: chunked" is used) or {@link Body} (iff not). To construct
 * a body within Membrane, {@link Body} is used by some helper method like
 * {@link Response.ResponseBuilder#body(String)}.
 * <p>
 * This class supports "streaming" the body: If a HTTP message is directly
 * forwarded by Membrane (without any component reading or changing the
 * message's body), the incoming network stream's buffer is directly written to
 * the output stream. This allows Membrane to perform very well in this
 * situation.
 * <p>
 * Repeatedly accessing the body (using streams or not) is supported. Body
 * Streams do not have to be read completely. Accessing the body from multiple
 * threads is illegal. Using a Body Stream after the Body as been accessed by
 * someone else (using streams or not) is illegal.
 * <p>
 * Failing to read a body is terminal: the {@link ReadingBodyException} is recorded (see
 * {@link #fail(IOException)}), the observers are notified via
 * {@link MessageObserver#bodyFailed(ReadingBodyException)} and every later access re-throws that same
 * exception instead of touching the (dead) stream again. This keeps the original cause visible rather
 * than replacing it with a follow-up error like "stream is closed".
 * <p>
 * Public instance methods must not throw {@link IOException}s. Throw an
 * unchecked {@link ReadingBodyException} or {@link WritingBodyException} instead.
 * (This is enforced by the BodyDoesntThrowIOExceptionTest .)
 */
public abstract class AbstractBody {
	private static final Logger log = LoggerFactory.getLogger(AbstractBody.class.getName());

	/**
	 * Largest body we will materialize into a single {@code byte[]}. A JVM cannot
	 * allocate an array larger than {@link Integer#MAX_VALUE} elements (some VMs stop
	 * a few bytes short), so anything above this can only be streamed, not buffered.
	 */
	static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

	/**
	 * How far consuming this body has got. Never assign directly: go through {@link #markAsRead()} or
	 * {@link #fail(IOException)}, which enforce the transitions.
	 */
	private BodyState state = UNREAD;

	protected final List<Chunk> chunks = new ArrayList<>();
	protected final List<MessageObserver> observers = new ArrayList<>(1);

	/**
	 * Whether the body was passed through without being retained. Orthogonal to {@link #state}: a
	 * streamed body still becomes {@link BodyState.Read} once it has been consumed completely.
	 */
	private boolean wasStreamed = false;

	public void read() {
		if (isRead())
			return;

		requireReadable();

		for (MessageObserver observer : observers)
			observer.bodyRequested(this);

		chunks.clear();
		try {
			readLocal();
		} catch (IOException e) {
			throw fail(e);
		}
		markAsRead();
	}

	public void discard() {
		if (state instanceof Failed(var cause)) {
			// Nothing left to drain and no reason to bother the caller: discard() is a best-effort
			// operation (see Message.discardBody()).
			log.debug("Not discarding body: reading it already failed ({}).", cause.getMessage());
			return;
		}
		read();
	}

	/**
	 * Guards every access to the body's content. Exhaustive over {@link BodyState}, so a new state
	 * cannot be added without deciding what each access point does with it.
	 *
	 * @throws ReadingBodyException if reading the body failed earlier. The stream is dead; retrying it
	 *         would only produce a follow-up error hiding the original cause.
	 */
	private void requireReadable() {
		switch (state) {
			case Failed(var cause) -> throw cause;
			case Unread ignored -> requireNotStreamed();
			case Read ignored -> requireNotStreamed();
		}
	}

	private void requireNotStreamed() {
		if (wasStreamed)
			throw new IllegalStateException("Cannot read body after it was streamed.");
	}

	/**
	 * The failure half of {@link #requireReadable()}, for subclasses that must not touch a dead stream
	 * but are reached on paths where streaming is legal.
	 *
	 * @throws ReadingBodyException if reading the body failed earlier.
	 */
	protected void throwIfFailed() {
		if (state instanceof Failed(var cause))
			throw cause;
	}

	/**
	 * Records that reading this body failed and notifies the observers.
	 * <p>
	 * The first failure wins: the recorded exception is never replaced and the observers are notified
	 * only once. Returns the exception so callers can write {@code throw fail(e);}.
	 */
	protected ReadingBodyException fail(IOException e) {
		if (state instanceof Failed(var cause))
			return cause;
		ReadingBodyException failure = new ReadingBodyException(e);
		state = new Failed(failure); // arm the latch before notifying, so a re-entering observer sees the failed state
		notifyBodyFailed(failure);
		return failure;
	}

	private void notifyBodyFailed(ReadingBodyException e) {
		// Copy and clear first: observers may access the list (e.g. ShadowingInterceptor) and this is the
		// last event fired on them, just as in markAsRead().
		List<MessageObserver> os = new ArrayList<>(observers);
		observers.clear();
		for (MessageObserver observer : os) {
			try {
				observer.bodyFailed(e);
			} catch (Exception ex) {
				// must not mask the original exception: Connection.bodyFailed() may throw
				e.addSuppressed(ex);
				log.warn("Observer {} failed while handling a body read error.", observer, ex);
			}
		}
	}

	protected void markAsRead() {
		switch (state) {
			case Read ignored -> {
				return;
			}
			// A failed body never becomes read: bodyComplete must not follow bodyFailed.
			case Failed ignored -> {
				return;
			}
			case Unread ignored -> state = READ;
		}

		onMarkedAsRead();

		for (MessageObserver observer : observers)
			observer.bodyComplete(this);

		observers.clear();
	}

	/**
	 * Hook for subclasses that keep their own completion state. Runs only when the body actually
	 * transitioned to read, so such state can never contradict {@link #isRead()} - in particular it
	 * stays unset for a body whose read failed.
	 */
	protected void onMarkedAsRead() {
		// no additional state to update
	}

	/**
	 * @return whether reading the body failed. In that case {@link #isRead()} stays <code>false</code>
	 *         and accessing the content throws the recorded {@link ReadingBodyException}.
	 */
	public boolean hasFailed() {
		return state instanceof Failed;
	}

	/**
	 * @return the exception recorded when reading this body failed, or <code>null</code>. Useful to tell
	 *         which of an exchange's bodies a {@link ReadingBodyException} belongs to.
	 */
	public ReadingBodyException getObservedException() {
		return state instanceof Failed(var cause) ? cause : null;
	}

	protected abstract void readLocal() throws IOException;

	/**
	 * Returns the body's content as a byte[] representation.
	 * <p>
	 * For example, getContent() might return a byte representation of
	 *
	 * <pre>
	 * Wikipedia in
	 *
	 * chunks.
	 * </pre>
	 *
	 * The return value does not differ whether "Transfer-Encoding: chunked" is
	 * used or not (see <a href="http://en.wikipedia.org/wiki/Chunked_transfer_encoding">Chunked Transfer Encoding</a>
	 * ), the example above is taken from there.
	 * <p>
	 * Please note that a new array is allocated when calling
	 * getContent(). If you do not need the body as one single byte[],
	 * you should therefore use {@link #getContentAsStream()} instead.
	 */
	public byte[] getContent() {
		requireReadable();
		read();
		long length = getLength();
		if (length > MAX_ARRAY_LENGTH)
			throw new BodyTooLargeException("Message body of " + length + " bytes is too large to load into memory (limit " + MAX_ARRAY_LENGTH + " bytes). Stream the message instead of calling getContent().");
		byte[] content = new byte[(int) length];
		int destPos = 0;
		for (Chunk chunk : chunks) {
			destPos = chunk.copyChunk(content, destPos);
		}
		return content;
	}

	public InputStream getContentAsStream() {
		requireReadable();
		read();
		return new BodyInputStream(chunks);
	}

	public void write(AbstractBodyTransferer out, boolean retainCopy) {
		// never (re-)transmit a body whose read failed: that would send a truncated body
		if (state instanceof Failed(var cause))
			throw cause;
		try {
			if (!isRead() && !retainCopy) {
				if (wasStreamed)
					log.warn("Streaming the body twice will not work.");
				for (MessageObserver observer : observers)
					observer.bodyRequested(this);
				wasStreamed = true;
				writeStreamed(out);
				return;
			}

			writeAlreadyRead(out);
		} catch (IOException e) {
			throw new WritingBodyException(e);
		}
	}

	protected abstract void writeAlreadyRead(AbstractBodyTransferer out) throws IOException;

	protected abstract void writeNotRead(AbstractBodyTransferer out) throws IOException;

	/**
	 * Is called when there are no observers that need to read the body. Streams the body without reading it
	 */
	protected abstract void writeStreamed(AbstractBodyTransferer out);

	/**
	 * Warning: Calling this method will trigger reading the body from the client, disabling "streaming".
	 * Use {@link #isRead()} to determine wether the body already has been read, if necessary.
	 *
	 * @return the body's total length in bytes. This is the full byte count and may exceed
	 *         {@link #MAX_ARRAY_LENGTH}, i.e. it can be larger than a {@link #getContent()}
	 *         array could hold.
	 */
	public long getLength() {
		read();

		long length = 0;
		for (Chunk chunk : chunks) {
			length += chunk.getLength();
		}
		return length;
	}

	/**
	 * Returns a reconstruction of the over-the-wire byte sequence received.
	 * <p>
	 * When "Transfer-Encoding: chunked" is used (see
	 * <a href="http://en.wikipedia.org/wiki/Chunked_transfer_encoding">Chunked Transfer Encoding</a> ), the return
	 * value might be (to follow the example from Wikipedia) a byte representation of
	 *
	 * <pre>
	 * 4
	 * Wiki
	 * 5
	 * pedia
	 * E
	 *  in
	 *
	 * chunks.
	 * 0
	 * </pre>
	 */
	public byte[] getRaw() {
		read();
        try {
            return getRawLocal();
        } catch (IOException e) {
            throw fail(e);
        }
    }

	protected abstract byte[] getRawLocal() throws IOException;

	protected boolean hasRelevantObservers() {
		return observers.stream().anyMatch(o -> !(o instanceof NonRelevantBodyObserver));
	}

	/**
	 * Supposes UTF-8 encoding. Should therefore not be used
	 * for primary functionality.
	 */
	@Override
	public String toString() {
		if (chunks.isEmpty()) {
			return "";
		}
		try {
			return new String(getRaw(), UTF_8);
		} catch (ReadingBodyException e) {
			log.error(e.getMessage());
			return "Error in body: " + e.getMessage();
		}
	}

	public boolean isRead() {
		return state instanceof Read;
	}

	/**
	 * Mutates the ArrayList observers without synchronization. Callers must only invoke
	 * it from the thread that owns the Body.
	 * @param observer MessageObserver to add
	 */
	public void addObserver(MessageObserver observer) {
		if (isRead()) {
			observer.bodyComplete(this);
			return;
		}
		if (state instanceof Failed(var cause)) {
			// the terminal event already happened: fire it immediately instead of registering an observer
			// that would never be called
			observer.bodyFailed(cause);
			return;
		}
		if (wasStreamed)
			log.warn("adding body observer after body was streamed.");
		observers.add(observer);
	}

	public List<MessageObserver> getObservers() {
		return observers;
	}

	public boolean wasStreamed() {
		return wasStreamed;
	}

	public Header getTrailer() {
		return null;
	}

	/**
	 *
	 */
	public boolean setTrailer(Header trailer) {
		return false;
	}

	public boolean hasTrailer() {
		return false;
	}
}
