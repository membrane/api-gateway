/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.apikey.stores.inConfig;

import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.util.List.of;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleKeyStoreTest {

    @Test
    void getScopes() throws UnauthorizedApiKeyException {
        SimpleKeyStore simpleKeyStore = new SimpleKeyStore();
        simpleKeyStore.setKeys(of(
                new Key(){{setValue("12345");}},
                new Key(){{
                    setValue("67890");
                    setScopes(of(
                            new Scope(){{setValue("admin");}},
                            new Scope(){{setValue("user");}})
                    );
                }})
        );
        assertEquals(empty(), simpleKeyStore.getScopes("12345"));
        assertEquals(Optional.of(of("admin", "user")), simpleKeyStore.getScopes("67890"));
        assertThrows(UnauthorizedApiKeyException.class, () -> simpleKeyStore.getScopes("ABCDE"));
    }
}