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

package com.predic8.membrane.core.multipart;

import com.predic8.membrane.core.http.ReadingBodyException;

import java.io.IOException;

/**
 * Indicates that an XOP/MTOM package cannot be reassembled, e.g. because a referenced part is
 * missing. The message is written for the sender and names only values from its own message.
 * <p>
 * Travels wrapped in a {@link ReadingBodyException}, whose source says which message the package
 * belongs to.
 */
public class XopReassemblyException extends IOException {

    public XopReassemblyException(String message) {
        super(message);
    }
}
