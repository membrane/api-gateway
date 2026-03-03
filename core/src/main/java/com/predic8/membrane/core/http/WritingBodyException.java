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

package com.predic8.membrane.core.http;

/**
 * Indicates that an error occurred while writing the body of a message.
 *
 * The 'message' should already be enough to indicate the error.
 *
 * (No need to use {@link com.predic8.membrane.core.util.ExceptionUtil#concatMessageAndCauseMessages(Throwable)}.)
 */
public class WritingBodyException extends RuntimeException {
    public WritingBodyException(Exception e) {
        super(e);
    }
}
