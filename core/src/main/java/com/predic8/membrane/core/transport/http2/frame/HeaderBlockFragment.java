package com.predic8.membrane.core.transport.http2.frame;

public interface HeaderBlockFragment {
    byte[] getContent();

    int getHeaderBlockStartIndex();

    int getHeaderBlockLength();

    boolean isEndHeaders();
}
