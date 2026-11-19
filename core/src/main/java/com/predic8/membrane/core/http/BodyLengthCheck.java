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

/**
 * Whether a body is within a length limit, see {@link AbstractBody#readUpTo(long)} and
 * {@link Message#checkBodyLength(long)}.
 */
public sealed interface BodyLengthCheck {

    BodyLengthCheck WITHIN = new Within();

    /**
     * The body is at most as long as the limit. It is in memory, or it is passed on as it arrives because
     * its declared length is within the limit.
     */
    record Within() implements BodyLengthCheck {}

    /**
     * The body is longer than the limit.
     *
     * @param abandoned whether the rest of the body was left unread on its connection. The body has then
     *                  failed with a {@link BodyLengthExceededException}, and its connection is closed
     *                  instead of reused. Otherwise the body is in memory and can still be read.
     */
    record TooLong(boolean abandoned) implements BodyLengthCheck {}
}
