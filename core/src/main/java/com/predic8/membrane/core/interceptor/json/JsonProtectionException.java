/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.json;

public class JsonProtectionException extends Exception{
    private final String message;
    private final int line;
    private final int col;

    public JsonProtectionException(String msg, int line, int col) {
        this.message = msg;
        this.line = line;
        this.col = col;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }
}
