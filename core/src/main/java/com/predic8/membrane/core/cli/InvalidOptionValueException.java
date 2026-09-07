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

package com.predic8.membrane.core.cli;

/**
 * Signals that the value given for a command line option cannot be used, e.g. because it is not a
 * number or outside the allowed range. The message is meant to be printed to the user as it is.
 */
public class InvalidOptionValueException extends RuntimeException {

    public InvalidOptionValueException(String message) {
        super(message);
    }
}
