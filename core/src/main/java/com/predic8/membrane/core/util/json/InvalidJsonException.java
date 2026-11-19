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

package com.predic8.membrane.core.util.json;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Text that is not exactly one JSON value, see {@link JsonUtil#readSingleValue(String)}. The message says what is
 * wrong and where, without naming Jackson types. It is a {@link JsonProcessingException}, so callers that answer
 * invalid JSON keep doing so.
 */
public class InvalidJsonException extends JsonProcessingException {

    InvalidJsonException(String message, Throwable cause) {
        // No JsonLocation: the position is part of the message, and getMessage() adds no "[Source: ...]" suffix.
        super(message, cause);
    }
}
