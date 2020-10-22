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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.http.Chunk;

public class ByteUtil {

	private static Logger log = LoggerFactory.getLogger(ByteUtil.class.getName());

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

		List<Chunk> chunks = new ArrayList<Chunk>();

		while (!decompressor.finished()) {
			byte[] buf = new byte[1024];
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
				System.arraycopy(buf, 0, shortContent, 0, count);
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
            b |= (1 << position);
        else
            b &= ~(1 << position);
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

	public static int getNumberOfBits(int i){
		return Integer.SIZE-Integer.numberOfLeadingZeros(i);
	}
}
