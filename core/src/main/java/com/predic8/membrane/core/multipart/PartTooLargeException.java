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

import com.predic8.membrane.core.http.Header;

import java.io.IOException;

/**
 * Thrown by {@link PartScanner#forEachPart} when a part's body exceeds the given maximum size.
 * Carries the header of the offending part, so a caller can report which attachment of an upload
 * was rejected.
 */
public class PartTooLargeException extends IOException {

    private final transient Header partHeader;

    PartTooLargeException(Header partHeader, int limit, Throwable cause) {
        super("Part exceeds the maximum size of " + limit + " bytes.", cause);
        this.partHeader = partHeader;
    }

    public Header getPartHeader() {
        return partHeader;
    }
}
