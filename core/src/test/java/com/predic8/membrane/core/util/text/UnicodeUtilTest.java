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
package com.predic8.membrane.core.util.text;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnicodeUtilTest {

    @Test
    void recognizesAUtf8Bom() {
        assertTrue(UnicodeUtil.startsWithByteOrderMark(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, 3));
    }

    @Test
    void recognizesAUtf16BigEndianBom() {
        assertTrue(UnicodeUtil.startsWithByteOrderMark(new byte[]{(byte) 0xFE, (byte) 0xFF, 0x00}, 3));
    }

    @Test
    void recognizesAUtf16LittleEndianBom() {
        assertTrue(UnicodeUtil.startsWithByteOrderMark(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00}, 3));
    }

    @Test
    void doesNotRecognizeOrdinaryXmlBytesAsABom() {
        assertFalse(UnicodeUtil.startsWithByteOrderMark("<gr".getBytes(UTF_8), 3));
    }

    @Test
    void aTruncatedUtf8BomIsNotMistakenForAUtf16Bom() {
        // The first two bytes of a UTF-8 BOM (EF BB) are neither UTF-16 BOM pattern.
        assertFalse(UnicodeUtil.startsWithByteOrderMark(new byte[]{(byte) 0xEF, (byte) 0xBB}, 2));
    }

    @Test
    void aSingleByteIsNeverEnoughToRecognizeABom() {
        assertFalse(UnicodeUtil.startsWithByteOrderMark(new byte[]{(byte) 0xFF}, 1));
    }

    @Test
    void anEmptyPrefixIsNotABom() {
        assertFalse(UnicodeUtil.startsWithByteOrderMark(new byte[0], 0));
    }

}