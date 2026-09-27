/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util.xml;

import org.apache.commons.io.ByteOrderMark;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XMLEncodingUtilTest {

    @Test
    void recognizesAUtf8Bom() {
        assertEquals(ByteOrderMark.UTF_8,
                XMLEncodingUtil.getByteOrderMark(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '<'}));
    }

    @Test
    void recognizesAUtf16Bom() {
        assertEquals(ByteOrderMark.UTF_16BE, XMLEncodingUtil.getByteOrderMark("<a/>".getBytes(UTF_16)));
    }

    @Test
    void recognizesAUtf16LittleEndianBom() {
        assertEquals(ByteOrderMark.UTF_16LE,
                XMLEncodingUtil.getByteOrderMark(new byte[]{(byte) 0xFF, (byte) 0xFE, '<', 0x00}));
    }

    @Test
    void ordinaryXmlBytesAreNotABom() {
        assertNull(XMLEncodingUtil.getByteOrderMark("<greeting/>".getBytes(UTF_8)));
    }

    @Test
    void aTruncatedUtf8BomIsNotABom() {
        assertNull(XMLEncodingUtil.getByteOrderMark(new byte[]{(byte) 0xEF, (byte) 0xBB}));
    }

    @Test
    void emptyAndNullAreNotABom() {
        assertNull(XMLEncodingUtil.getByteOrderMark(new byte[0]));
        assertNull(XMLEncodingUtil.getByteOrderMark(null));
    }

    /**
     * UTF-32 is not in {@link XMLEncodingUtil#XML_BYTE_ORDER_MARKS}, so its little-endian mark is
     * reported as the UTF-16 one it starts with - the same answer as before, and the parsers in use
     * do not decode UTF-32 either way.
     */
    @Test
    void utf32IsNotDetectedSeparately() {
        assertEquals(ByteOrderMark.UTF_16LE,
                XMLEncodingUtil.getByteOrderMark(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x00}));
    }

    @Test
    void readsTheEncodingFromAPrologBehindAUtf8Bom() {
        byte[] withBom = concat(ByteOrderMark.UTF_8.getBytes(),
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a/>".getBytes(UTF_8));

        assertEquals("ISO-8859-1", XMLEncodingUtil.getEncodingFromXMLProlog(withBom));
    }

    @Test
    void readsTheEncodingFromAPrologWithoutABom() {
        assertEquals("ISO-8859-1", XMLEncodingUtil.getEncodingFromXMLProlog(
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a/>".getBytes(UTF_8)));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
