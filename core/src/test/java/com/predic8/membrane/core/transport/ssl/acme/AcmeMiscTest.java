package com.predic8.membrane.core.transport.ssl.acme;

import com.predic8.membrane.core.transport.ssl.AcmeSSLContext;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.transport.ssl.AcmeSSLContext.computeHostList;
import static org.junit.jupiter.api.Assertions.*;

public class AcmeMiscTest {

    private final String[] FOO = new String[]{ "foo.com" };
    private final String[] AST = new String[]{ "*.com" };
    private final String[] AST2 = new String[]{ "*.de", "*.com" };

    @Test
    public void hostList() {
        assertArrayEquals(FOO, computeHostList(FOO, null));
        assertArrayEquals(FOO, computeHostList(FOO, "foo.com"));
        assertArrayEquals(AST, computeHostList(FOO, "*.com"));
        assertArrayEquals(AST2, computeHostList(FOO, "*.de *.com"));
        assertThrows(RuntimeException.class, () -> computeHostList(FOO, "*.org"));
    }
}
