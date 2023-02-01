package com.predic8.membrane.core.http.cookie;

import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

public class MessageBytesTest {

    @Test
    @Disabled // See implemtation of method
    void isNull() {
        assertTrue(MessageBytes.newInstance().isNull());
    }

    @Test
    void equalsTestWithString() {
        String message = "äöüÄÖÜß";

        //noinspection AssertBetweenInconvertibleTypes
        assertEquals(getMessageBytes(message), message);
    }

    @Test
    void testEquals() {
        assertEquals(getMessageBytes("äöüÄÖÜß"), getMessageBytes("äöüÄÖÜß"));
    }

    @NotNull
    private static MessageBytes getMessageBytes(String s) {
        MessageBytes b1 = MessageBytes.newInstance();
        byte[] buf = s.getBytes(ISO_8859_1_CHARSET);
        b1.setBytes(buf, 0, buf.length);
        return b1;
    }
}