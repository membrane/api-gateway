package com.predic8.membrane.core.prettifier;

import org.jetbrains.annotations.*;

import java.nio.charset.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractPrettifier implements Prettifier {

    protected static @NotNull Charset getCharset(Charset charset) {
        return (charset != null) ? charset : UTF_8;
    }
}
