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
public enum BodyLengthCheck {

    /**
     * The body is at most as long as the limit. It is in memory, or it is passed on as it arrives because
     * its declared length is within the limit.
     */
    WITHIN,

    /**
     * The body is longer than the limit. What became of it is the body's state: one that was not read to
     * its end {@linkplain AbstractBody#hasFailed() has failed} with a {@link BodyLengthExceededException},
     * so that its connection is not reused; otherwise it is in memory and can still be read.
     */
    TOO_LONG
}
