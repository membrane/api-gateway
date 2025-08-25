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

package com.predic8.membrane.core.util.soap;

import com.predic8.membrane.core.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_SOAP_XML;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;

public enum SoapVersion {
    SOAP_11("1.1"), SOAP_12("1.2");

    private final String value;

    SoapVersion(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public String getContentType() {
        return switch (this) {
            case SOAP_11 -> TEXT_XML;
            case SOAP_12 -> APPLICATION_SOAP_XML;
        };
    }

    public static SoapVersion parse(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new ConfigurationException("Invalid soap version: " + s);
        }
        return switch (s.trim()) {
            case "1.1","11" -> SOAP_11;
            case "1.2","12" -> SOAP_12;
            default ->  throw new ConfigurationException("SOAP version %s is not supported.".formatted(s));
        };
    }
}
