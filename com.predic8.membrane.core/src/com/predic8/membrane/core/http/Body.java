/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.HttpUtil;

public class Body {

	private Log log = LogFactory.getLog(Body.class.getName());

	boolean read;

	private boolean chunked;

	private List<Chunk> chunks = new ArrayList<Chunk>();

	private InputStream inputStream;

	private int length;

	public Body() {

	}

	public Body(String body) {
		chunks.add(new Chunk(body));
		read = true; //because we do not have something to read
		length = body.length();
	}

	public Body(Body body) {
		chunks = new ArrayList<Chunk>(body.chunks);
		read = body.read;
		chunked = body.chunked;
	}

	public Body(InputStream in, int length) throws IOException {
		this.inputStream = in;
		this.length = length;
		
	}

	public Body(InputStream in, boolean chunked) {
		log.debug("Body(Stream in, boolean chunked) " + chunked);
		this.chunked = chunked;
		this.inputStream = in;
	}

	public void read() {
		log.debug("read called, chunked = " + chunked);
		if (read)
			return;
		
		chunks.clear();
		try {
			if (!chunked) {
				chunks.add(new Chunk(ByteUtil.readByteArray(inputStream, length)));
				read = true;
				return;
			}
			readChunks(inputStream);
			read = true;
		} catch (IOException e) {
			log.error(e);
			throw new 	RuntimeException("could not read body");
		}
	}

	private void readChunks(InputStream in) throws IOException {
		int chunkSize = HttpUtil.readChunkSize(in);
		while (chunkSize > 0) {
			
			byte[] chunk = ByteUtil.readByteArray(in, chunkSize);
			
			chunks.add(new Chunk(chunk));
			
			in.read(); // CR
			in.read(); // LF
			chunkSize = HttpUtil.readChunkSize(in);
		}
		in.read(); // CR
		in.read(); // LF
	}

	public byte[] getContent() {
		if (!read) {
			read();
		}
		byte[] content = new byte[getLength()]; 
		int destPos = 0;
		for (Chunk chunk: chunks) {
			destPos = chunk.copyChunk(content, destPos);
		} 
		return content;
	}

	public InputStream getBodyAsStream() throws IOException {
		return new ByteArrayInputStream(getContent());
	}

	
	/**
	 * the caller of this method is responsible to adjust the header accordingly
	 * e.g. the fields Transfer-Encoding and Content-Length
	 * Therefore this method has access modifier default   
	 * 
	 * @param bytes
	 */
	void setContent(byte[] bytes) {
		chunks.clear();
		chunks.add(new Chunk(bytes));
		chunked = false;
	}

	
	public void write(OutputStream out) throws IOException {
		if (!read) {
			writeNotRead(out);
			return;
		}

		writeAlreadyRead(out);
	}

	private void writeAlreadyRead(OutputStream out) throws IOException {
		if (getLength() == 0) {
			return;
		}

		if (chunked) {
			for (Chunk chunk : chunks) {
				chunk.write(out);
			}
			out.write("0".getBytes());
			out.write(Constants.CRLF.getBytes());
			out.write(Constants.CRLF.getBytes());
			return;
		}
		out.write(getContent(), 0, getLength());
		out.flush();
	}

	private void writeNotRead(OutputStream out) throws IOException {
		if (chunked) {
			writeNotReadChunked(out);
		} else {
			writeNotReadUnchunked(out);
		}
		read = true;
	}

	private void writeNotReadUnchunked(OutputStream out) throws IOException {
		byte[] buffer = new byte[8192];
		
		int totalLength = 0;
		int length = 0;
		chunks.clear();
		while ((this.length > totalLength || this.length == -1) && (length = inputStream.read(buffer)) > 0) {
			totalLength += length;
			out.write(buffer, 0, length);
			out.flush();
			byte[] chunk = new byte[length];
			System.arraycopy(buffer, 0, chunk, 0, length);
			chunks.add(new Chunk(chunk));
		}
	}

	private void writeNotReadChunked(OutputStream out) throws IOException {
		log.debug("writeNotReadChunked");
		int chunkSize = HttpUtil.readChunkSize(inputStream);
		while (chunkSize > 0) {
			String size = Integer.toHexString(chunkSize);
			out.write(size.getBytes());
			
			out.write(Constants.CRLF_BYTES);
			byte[] chunk = ByteUtil.readByteArray(inputStream, chunkSize);
			out.write(chunk);
			chunks.add(new Chunk(chunk));
			out.write(Constants.CRLF_BYTES);
			inputStream.read(); // CR
			inputStream.read(); // LF
			chunkSize = HttpUtil.readChunkSize(inputStream);
			out.flush();
		}
		inputStream.read(); // CR
		inputStream.read(); // LF-
		out.write("0".getBytes());
		out.write(Constants.CRLF_BYTES);
		out.write(Constants.CRLF_BYTES);
		out.flush();
	}

	
	public int getLength() {
		if (!read)
			read();
		
		int length = 0;
		for (Chunk chunk : chunks) {
			length += chunk.getLength(); 
		}
		return length;
	}
	
	private int getRawLength() {
		if (chunks.size() == 0)
			return 0;
		int length = getLength();
		for (Chunk chunk : chunks) {
			length += Long.toHexString(chunk.getLength()).getBytes().length;
			length += 2*Constants.CRLF_BYTES.length;
		}
		length += "0".getBytes().length;
		length += 2*Constants.CRLF_BYTES.length;
		return length;
	}
	
	public byte[] getRaw() {
		if (!read) {
			read();
		}
		if (chunked) {
			byte[] raw = new byte[getRawLength()];
			int destPos = 0;
			for (Chunk chunk : chunks) {
				
				destPos = chunk.copyChunkLength(raw, destPos, this); 
				
				destPos = copyCRLF(raw, destPos); 
				
				destPos = chunk.copyChunk(raw, destPos);
				
				destPos = copyCRLF(raw, destPos); 
				
			}
			
			destPos = copyLastChunk(raw, destPos);
			
			destPos = copyCRLF(raw, destPos); 
			return raw;
		} 
		if (chunks.size() == 0) {
			log.debug("size of chunks list: " + chunks.size() + "  " + hashCode());
			log.debug("chunks size is: " + chunks.size() + " at time: "+ System.currentTimeMillis());
			return new byte[0];
		}
		
		byte[] raw = new byte[getLength()];
		int destPos = 0;
		for (Chunk chunk : chunks) {
			destPos = chunk.copyChunk(raw, destPos);
		}
		return raw;
	}

	private int copyLastChunk(byte[] raw, int destPos) {
		System.arraycopy("0".getBytes(), 0, raw, destPos, "0".getBytes().length);
		destPos += "0".getBytes().length;
		destPos = copyCRLF(raw, destPos); 
		return destPos;
	}

	private int copyCRLF(byte[] raw, int destPos) {
		System.arraycopy(Constants.CRLF_BYTES, 0, raw, destPos, 2);
		return destPos += 2;
	}
	
	@Override
	public String toString() {
		if (chunks.size() == 0) {
			return "";
		}
		return new String(getRaw());
	}
	
}
