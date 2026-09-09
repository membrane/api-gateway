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

package com.predic8.membrane.annot.generator.util;

import javax.annotation.processing.FilerException;

public class FilerUtil {

    /**
     * Whether a {@link FilerException} means "this file was already created in an earlier round",
     * which the generators tolerate, as opposed to a real failure.
     * <p>
     * The only way to tell them apart is the message text, which is unspecified and differs per
     * compiler. javac uses the first two; the third is kept for compilers that phrase it differently.
     */
    public static boolean isAlreadyCreated(FilerException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Attempt to recreate a file for")     // javac, createSourceFile()
                            || msg.contains("Attempt to reopen a file for path")  // javac, createResource()
                            || msg.contains("Source file already created"));
    }
}
