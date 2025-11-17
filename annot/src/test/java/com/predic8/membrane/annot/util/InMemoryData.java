package com.predic8.membrane.annot.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * A bunch of files (and their content), we hold in memory. Used to simulate the file system.
 */
public class InMemoryData {
    public final Map<URI, byte[]> content = new HashMap<>();
}
