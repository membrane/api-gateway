/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.prettifier;

import java.io.*;
import java.nio.charset.*;

/**
 * Returns the reference to the provided byte array without copying.
 * Note: zero-copy aliasing — callers must not mutate the returned array unless they own it.
 */
public final class NullPrettifier implements Prettifier {

    public static final NullPrettifier INSTANCE = new NullPrettifier();

    private NullPrettifier() {
    }

    @Override
    public byte[] prettify(byte[] c, Charset charset) {
        return c; // Ignore charset
    }

    @Override
    public byte[] prettify(InputStream is, Charset charset) throws IOException {
        return is.readAllBytes();
    }
}
