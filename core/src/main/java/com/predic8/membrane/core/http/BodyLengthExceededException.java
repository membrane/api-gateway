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
package com.predic8.membrane.core.http;

import java.io.IOException;

/**
 * The cause recorded on a body that {@link AbstractBody#readUpTo(long)} abandoned because it is longer than
 * the limit. Lets a later reader of the failed body tell a limit apart from a broken connection.
 * <p>
 * Unlike {@link BodyTooLargeException}, which says that a body cannot be held in one array, this is about a
 * limit someone chose.
 */
public class BodyLengthExceededException extends IOException {

    private final long maxLength;

    public BodyLengthExceededException(long maxLength) {
        super("Body is longer than %d bytes.".formatted(maxLength));
        this.maxLength = maxLength;
    }

    /**
     * @return the limit in bytes the body exceeded
     */
    public long getMaxLength() {
        return maxLength;
    }
}
