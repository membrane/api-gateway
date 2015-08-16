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

package com.predic8.membrane.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.http.Chunk;

public class ByteUtil {

	private static Log log = LogFactory.getLog(ByteUtil.class.getName());

	public static byte[] readByteArray(InputStream in, int length) throws IOException {
		if (length < 0)
			return getByteArrayData(in);
		
		byte[] content = new byte[length];
		int offset = 0;
		int count = 0;
		while (offset < length && (count = in.read(content, offset, length - offset)) >= 0) {
			offset += count;
		}
		return content;
	}

	public static byte[] getByteArrayData(InputStream stream) throws IOException {

		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		byte[] buffer = new byte[2048];
		while (true) {
			int read = stream.read(buffer);
			if (read < 0)
				break;
			bos.write(buffer, 0, read);
		}

		try {
			bos.close();
		} catch (IOException e) {
			log.error("", e);
		}

		return bos.toByteArray();
	}

	public static byte[] getDecompressedData(byte[] compressedData) throws IOException {
		Inflater decompressor = new Inflater(true);
		decompressor.setInput(compressedData);

		byte[] buf = new byte[1024];
		
		List<Chunk> chunks = new ArrayList<Chunk>();
		
		while (!decompressor.finished()) {
			int count;
			try {
				count = decompressor.inflate(buf);
			} catch (DataFormatException e) {
				throw new IOException(e);
			}
			if (buf.length == count) {
				Chunk chunk = new Chunk(buf);
				chunks.add(chunk);
			} else if (count < buf.length){
				byte[] shortContent = new byte[count];
				for (int j = 0; j < count; j ++) {
					shortContent[j] = buf[j];
				}
				Chunk chunk = new Chunk(shortContent);
				chunks.add(chunk);
			}
		}
		
		log.debug("Number of decompressed chunks: " + chunks.size());
		if (chunks.size() > 0) {
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			
			for (Chunk chunk : chunks) {
				chunk.write(bos);
			}
			
			try {
				bos.close();
			} catch (IOException e) {
			}
			return bos.toByteArray();	
		} 
		
		return null;
	}

}
