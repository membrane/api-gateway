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

package com.predic8.membrane.core.transport.http.method;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MethodValidatorTest {

    @Test
    void permissiveDefaultIsTokenValidator() {
        assertInstanceOf(RFC9110MethodValidator.class, MethodValidator.permissive());
    }

    @Test
    void tokenValidatorAcceptsKnownAndCustomTokens() {
        MethodValidator v = new RFC9110MethodValidator();
        assertTrue(v.isValid("GET"));
        assertTrue(v.isValid("POST"));
        assertTrue(v.isValid("PROPFIND"));            // WebDAV
        assertTrue(v.isValid("BASELINE-CONTROL"));    // DeltaV, contains hyphen (valid tchar)
        assertTrue(v.isValid("custom"));              // lowercase is a valid token
    }

    @Test
    void tokenValidatorRejectsNonTokenAndOverlong() {
        MethodValidator v = new RFC9110MethodValidator();   // default maxLength 20
        assertFalse(v.isValid(null));
        assertFalse(v.isValid(""));
        assertFalse(v.isValid("GE T"));               // space is not a tchar
        assertFalse(v.isValid("GET\n"));              // control char
        assertFalse(v.isValid("A".repeat(21)));       // longer than 20
        assertTrue(v.isValid("A".repeat(20)));        // exactly 20 is ok
    }

    @Test
    void knownValidatorAcceptsOnlyStandardVerbs() {
        MethodValidator v = new KnownMethodValidator();
        assertTrue(v.isValid("GET"));
        assertTrue(v.isValid("OPTIONS"));
        assertFalse(v.isValid("PROPFIND"));
        assertFalse(v.isValid("get"));                // case-sensitive
    }

    @Test
    void uppercaseValidatorAcceptsUppercaseOnly() {
        UppercaseMethodValidator v = new UppercaseMethodValidator();
        v.setMaxLength(8);
        assertTrue(v.isValid("GET"));
        assertTrue(v.isValid("CONNECT"));
        assertTrue(v.isValid("PROPFIND"));            // 8 uppercase letters, within the length cap
        assertFalse(v.isValid("get"));                // lowercase rejected
        assertFalse(v.isValid("GE-T"));               // hyphen rejected
        assertFalse(v.isValid("PROPPATCH"));          // 9 chars, exceeds length cap of 8
    }

    @Test
    void maxLengthIsConfigurable() {
        RFC9110MethodValidator three = new RFC9110MethodValidator();
        three.setMaxLength(3);
        assertFalse(three.isValid("POST"));

        RFC9110MethodValidator four = new RFC9110MethodValidator();
        four.setMaxLength(4);
        assertTrue(four.isValid("POST"));
    }
}
