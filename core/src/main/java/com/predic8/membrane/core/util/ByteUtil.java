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

import com.predic8.membrane.core.http.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class ByteUtil {

	private static final Logger log = LoggerFactory.getLogger(ByteUtil.class.getName());

	public static byte[] readByteArray(InputStream in, int length) throws IOException {
		if (length < 0)
			return in.readAllBytes();

		byte[] content = new byte[length];
		int offset = 0;
		int count;
		while (offset < length && (count = in.read(content, offset, length - offset)) >= 0) {
			offset += count;
		}
		return content;
	}

	public static void readStream(InputStream stream) throws IOException {
		byte[] buffer = new byte[2048];
		while (true) {
			int read = stream.read(buffer);
			if (read < 0)
				break;
		}
	}

	public static byte[] getDecompressedData(byte[] compressedData) throws IOException {
		Inflater decompressor = new Inflater(true);
		decompressor.setInput(compressedData);

		List<Chunk> chunks = new ArrayList<>();

		while (!decompressor.finished()) {
			byte[] buf = new byte[1024];
			int count;
			try {
				count = decompressor.inflate(buf);
			} catch (DataFormatException e) {
				throw new IOException(e);
			}
			// Zero output is valid when the stream has finished (including an empty body).
			// Otherwise, retrying without new input or a dictionary can loop forever.
			if (count == 0 && !decompressor.finished()) {
				// A preset dictionary contains bytes shared by compressor and decoder in advance.
				// Defensive check: raw deflate has no header identifying a required dictionary.
				if (decompressor.needsDictionary()) {
					log.info("Deflate stream requires a preset dictionary.");
					throw new IOException("Deflate stream requires a preset dictionary.");
				}
				// The complete compressed body was supplied, so no more input can arrive.
				if (decompressor.needsInput()) {
					log.info("Truncated deflate stream.");
					throw new IOException("Truncated deflate stream.");
				}
				// Reject any other stalled state instead of retrying indefinitely.
				log.info("Deflate decompression made no progress.");
				throw new IOException("Deflate decompression made no progress.");
			}
			if (buf.length == count) {
				Chunk chunk = new Chunk(buf);
				chunks.add(chunk);
			} else if (count < buf.length){
				byte[] shortContent = new byte[count];
				System.arraycopy(buf, 0, shortContent, 0, count);
				Chunk chunk = new Chunk(shortContent);
				chunks.add(chunk);
			}
		}

		log.debug("Number of decompressed chunks: {}",chunks.size());
		if (!chunks.isEmpty()) {

			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			for (Chunk chunk : chunks) {
				bos.write(chunk.content());
			}
			return bos.toByteArray();
		}
		return null;
	}

	public static int getValueOfBits(byte b, int minBitPosition, int maxBitPosition){
        byte result = 0;
        for(int i = minBitPosition; i <= maxBitPosition; i++){
            if(getBitValueBigEndian(b,i))
                result = setBitValueBigEndian(result,i,true);
        }
        return Byte.toUnsignedInt(result);
    }

	public static boolean getBitValueBigEndian(byte b, int position){
        return getBitValue(b,position,true);
    }

	public static boolean getBitValue(byte b, int position, boolean isBigEndian){
        if(isBigEndian)
            position = 7 - position;
        return (b & (1 << position)) != 0;
    }

	public static byte setBitValue(byte b, int position, boolean value, boolean isBigEndian){
        if(isBigEndian)
            position = 7 - position;

        if(value)
            b |= (byte) (1 << position);
        else
            b &= (byte) ~(1 << position);
        return b;
    }

	public static byte setBitValueBigEndian(byte b, int position, boolean value) {
        return setBitValue(b,position,value,true);
    }

    public static byte setBitValues(byte b, int beginning, int end, int value){
		byte valByte = (byte)value;
		for(int i = beginning; i <= end;i++)
			if(getBitValueBigEndian(valByte,i))
				b = setBitValueBigEndian(b,i,true);
		return b;
	}

	public static byte setBitValuesBigEndian(byte b, int beginning, int end, int value){
    	return setBitValues(b,beginning, end,value);
	}
}
