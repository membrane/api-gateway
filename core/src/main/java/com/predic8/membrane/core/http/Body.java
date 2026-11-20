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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.annot.Constants.*;

/**
 * A message body that is not chunked: framed either by a declared length ({@code Content-Length}) or,
 * if the length is unknown ({@code -1}), by the end of the stream. Streamed if possible. Use
 * {@link ChunkedBody} instead if "Transfer-Encoding: chunked" is set on the input; which of the two is
 * created is decided in {@code Message.createBody}.
 * <p>
 * The "Transfer-Encoding" of the output is not determined by this class hierarchy, but by
 * {@link AbstractBodyTransferer} and its subclasses.
 * <p>
 * The caller is responsible to adjust the header accordingly,
 * e.g. the fields Transfer-Encoding and Content-Length.
 * <p>
 * The body is consumed in exactly one of three ways: read into memory ({@link #read()}), streamed
 * through without being retained ({@link #write(AbstractBodyTransferer, boolean)} with
 * {@code retainCopy == false}), or discarded ({@link #discard()}). None of them reads beyond the
 * declared length, since the bytes after it belong to the next message on the connection. A body of
 * known length that ends early is marked failed (see {@link AbstractBody#hasFailed()}), never read.
 */
public class Body extends AbstractBody {

	private static final Logger log = LoggerFactory.getLogger(Body.class.getName());

	private final static int BUFFER_SIZE;
	private final static int MAX_CHUNK_LENGTH;

	/**
	 * A G1 region is at least 1 MiB, so from half of that on, an array can be a humongous object, which takes
	 * whole regions. A piece of a power-of-two size plus its array header would take one more region than its
	 * content needs, so such pieces leave {@link #ARRAY_HEADER_ROOM} free.
	 */
	private static final int HUMONGOUS_PIECE_LENGTH = 512 * 1024;

	/**
	 * At least the size of an array header, whatever the object layout of the JVM.
	 */
	private static final int ARRAY_HEADER_ROOM = 64;

	static {
		var bufferSize = System.getProperty(MEMBRANE_CORE_HTTP_BODY_BUFFERSIZE);
		BUFFER_SIZE = bufferSize == null ? MEMBRANE_CORE_HTTP_BODY_BUFFERSIZE_DEFAULT : Integer.parseInt(bufferSize);
		var maxChunkLength = System.getProperty(MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH);
		MAX_CHUNK_LENGTH = maxChunkLength == null ? MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH_DEFAULT : Integer.parseInt(maxChunkLength);
	}

	private final InputStream inputStream;
	private final long length;
	private long streamedLength;

	/**
	 * Creates a body of unknown length that ends with the stream.
	 *
	 * @param in the stream to read the body from, usually the connection's
	 */
	public Body(InputStream in) {
		this(in, -1);
	}

	/**
	 * Creates a body that is read from a stream.
	 *
	 * @param in the stream to read the body from, usually the connection's
	 * @param length the declared length in bytes, or {@code -1} if unknown, in which case the body ends
	 *        with the stream
	 */
	public Body(InputStream in, long length) {
		this.inputStream = in;
		this.length = length;
	}

	/**
	 * Creates a body from content already in memory. The body is read from the start.
	 *
	 * @param content the complete body
	 */
	public Body(byte[] content) {
		this.inputStream = null;
		this.length = content.length;
		chunks.clear();
		chunks.add(new Chunk(content));
		markAsRead(); // because we do not have something to read
	}

	/**
	 * Creates a body from content already in memory as chunks. The chunks are shared, not copied; later
	 * changes to the list do not affect the body. The body is read from the start.
	 *
	 * @param content the complete body
	 */
	Body(List<Chunk> content) {
		this.inputStream = null;
		this.length = content.stream().mapToLong(Chunk::getLength).sum();
		chunks.addAll(content);
		markAsRead();
	}

	/**
	 * Reads the body into {@link #chunks}, passing each chunk to the observers. The chunks start at the
	 * buffer size and double up to {@code MAX_CHUNK_LENGTH}, so memory follows the bytes that arrive, not
	 * the length the sender announces. Large chunks leave room for the array header, see
	 * {@link #HUMONGOUS_PIECE_LENGTH}.
	 * <p>
	 * A body of known length is not read at all if it is longer than {@code maxLength}, and never beyond
	 * its length. A body of unknown length is read no further than one byte beyond {@code maxLength}.
	 *
	 * @throws EOFException if a body of known length ends early
	 */
	@Override
	protected boolean readChunksWithin(long maxLength) throws IOException {
		if (length > maxLength)
			return false;

		final long end;
		if (length != -1)
			end = length;
		else // one byte beyond the limit is enough to tell that the body is too long
			end = maxLength == Long.MAX_VALUE ? Long.MAX_VALUE : maxLength + 1;

		long read = 0;
		int capacity = Math.min(BUFFER_SIZE, MAX_CHUNK_LENGTH);
		while (read < end) {
			final var pieceCapacity = capacity >= HUMONGOUS_PIECE_LENGTH ? capacity - ARRAY_HEADER_ROOM : capacity;
			final var pieceLength = (int) Math.min(pieceCapacity, end - read);
			final var piece = new byte[pieceLength];
			final var n = inputStream.readNBytes(piece, 0, pieceLength);
			if (n > 0) {
				addChunk(new Chunk(n == pieceLength ? piece : Arrays.copyOf(piece, n)));
				read += n;
			}
			if (n < pieceLength) { // end of the stream
				if (length != -1)
					throw new EOFException("Body ended %d bytes before the declared length of %d while being read.".formatted(length - read, length));
				return true;
			}
			capacity = (int) Math.min(2L * capacity, MAX_CHUNK_LENGTH);
		}
		return read <= maxLength;
	}

	private void addChunk(Chunk chunk) {
		chunks.add(chunk);
		for (MessageObserver observer : observers)
			observer.bodyChunk(chunk);
	}

	/**
	 * @return the length declared when the body was created, or {@code -1} if it ends with the stream
	 */
	@Override
	public long getDeclaredLength() {
		return length;
	}

	/**
	 * @return <code>true</code>: a discarded body is not retained
	 */
	@Override
	protected boolean dropsContentOnDiscard() {
		return true;
	}

	/**
	 * See {@code skipBodyContent()} for how bodies of unknown length and bodies that end early are handled.
	 */
	@Override
	protected DropOutcome dropContent() throws IOException {
		return skipBodyContent();
	}

	/**
	 * Drops the bytes the body still owes, so that the connection can carry the next message.
	 * <p>
	 * A body of <b>unknown length</b> is not drained. It ends only when the peer closes the connection,
	 * so reading to its end would mean waiting for that close. Such a close-delimited body (HTTP/1.0
	 * without a length, {@code Connection: close}, or the "the server will send EOF" fallback, see
	 * {@code Message.createBody}) leaves no connection to reclaim, even where the headers still say
	 * keep-alive. So the stream is left untouched and the body is abandoned rather than read, which
	 * makes sure the connection is closed instead of reused.
	 * <p>
	 * A body of <b>known length</b> is drained, feeding the relevant observers as it goes, and never
	 * reads beyond the declared length - the bytes after it belong to the next message. If the body ends
	 * early, whether by EOF or by a read timeout, that is a failure too: what the message promised is
	 * not there, so the connection is out of step and must not be reused.
	 *
	 * @throws IOException if reading fails or the body ends before its declared length
	 */
	private DropOutcome skipBodyContent() throws IOException {
		if (length == -1)
			return new DropOutcome.Abandoned("Body of unknown length was not drained: it ends with the connection, which therefore cannot be reused.");

		boolean hasRelevantObserver = hasRelevantObservers();
		chunks.clear();

		long toSkip = length;
		byte[] buffer = new byte[(int) Math.min(BUFFER_SIZE, toSkip)];
		while (toSkip > 0) {
			// Bounded by what is still owed: reading further would eat into the next message. read(),
			// not skip(), because only read() tells EOF apart from "skipped nothing this time".
			int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, toSkip));
			if (read < 0)
				break;
			if (hasRelevantObserver && read > 0)
				for (MessageObserver observer : observers)
					observer.bodyChunk(buffer, 0, read);
			toSkip -= read;
		}

		if (toSkip > 0)
			throw new EOFException("Body ended %d bytes before the declared length of %d while being discarded.".formatted(toSkip, length));
		return DropOutcome.DRAINED;
	}

	/**
	 * Writes the content held in memory and finishes the transfer.
	 */
	@Override
	protected void writeAlreadyRead(AbstractBodyTransferer out) throws IOException {
		if (getLength() > 0) { // reads the body if it is not read yet
			requireContentRetained();
			// chunk by chunk: copying them into one array would hold the body twice, and fails above 2 GiB
			for (Chunk chunk : chunks)
				if (chunk.getLength() > 0)
					out.write(chunk);
		}
		out.finish(null);
	}

	/**
	 * Copies the body from the stream to {@code out} without retaining it, passing each piece to the
	 * observers. A body of known length is read up to that length and no further; a body of unknown
	 * length is read to the end of the stream. The transfer is finished and the body marked read only
	 * once it is complete.
	 *
	 * @throws ReadingBodyException if reading from the stream fails, or if a body of known length ends
	 *         early. The bytes received up to then have already been written to {@code out}.
	 * @throws WritingBodyException if writing to {@code out} fails
	 */
	@Override
	protected void writeStreamed(AbstractBodyTransferer out) {
		byte[] buffer = new byte[BUFFER_SIZE];

		long totalLength = 0;
		int length;
		chunks.clear();
		while (true) {
            try {
                // Bounded by what is still owed: reading further would eat into the next message.
                int toRead = this.length == -1 ? buffer.length : (int) Math.min(buffer.length, this.length - totalLength);
                if (!((this.length > totalLength || this.length == -1) && (length = inputStream.read(buffer, 0, toRead)) > 0))
                    break;
            } catch (IOException e) {
                throw fail(e);
            }
            totalLength += length;
			streamedLength += length;
			try {
				out.write(buffer, 0, length);
			} catch (IOException e) {
				throw new WritingBodyException(e);
			}
			for (MessageObserver observer : observers)
				observer.bodyChunk(buffer, 0, length);
		}
		if (this.length != -1 && totalLength < this.length)
			throw fail(new EOFException("Body ended %d bytes before the declared length of %d while being streamed.".formatted(this.length - totalLength, this.length)));
        try {
            out.finish(null);
        } catch (IOException e) {
            throw new WritingBodyException(e);
        }
        markAsRead();
	}

	/**
	 * @return for a streamed body, the number of bytes streamed so far; otherwise, as in
	 *         {@link AbstractBody#getLength()}
	 */
	@Override
	public long getLength() {
		if (wasStreamed())
			return streamedLength;
		return super.getLength();
	}

	/**
	 * @return the content read into memory, or an empty array if there is none
	 */
	@Override
	protected byte[] getRawLocal() {
		if (chunks.isEmpty()) {
			log.debug("Chunks list is empty: {}", hashCode());
			return new byte[0];
		}
		return getContent();
	}
}
