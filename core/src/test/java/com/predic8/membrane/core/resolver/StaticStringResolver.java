package com.predic8.membrane.core.resolver;

import com.predic8.membrane.core.util.functionalInterfaces.*;

import java.io.*;
import java.util.*;

public class StaticStringResolver implements Resolver {

    /**
     * Returns the parameter back as InputStream. Useful for tests.
     * @param schema String with content
     * @return InputStream of the provided String
     */
    @Override
    public InputStream resolve(String schema) {
        return new ByteArrayInputStream(schema.getBytes());
    }

    @Override
    public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) {

    }

    @Override
    public List<String> getChildren(String url) {
        return List.of();
    }

    @Override
    public long getTimestamp(String url) {
        return 0;
    }
}
