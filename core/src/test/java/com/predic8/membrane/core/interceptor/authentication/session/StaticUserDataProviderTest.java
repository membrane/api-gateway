package com.predic8.membrane.core.interceptor.authentication.session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StaticUserDataProviderTest {
    @Test
    public void test() {
        StaticUserDataProvider sudp = new StaticUserDataProvider();
        Assertions.assertTrue(
                sudp.isHashedPassword("$5$9d3c06e19528aebb$cZBA3E3SdoUvk865.WyPA5iNUEA7uwDlDX7D5Npkh8/"));
        Assertions.assertTrue(
                sudp.isHashedPassword("$5$99a6391616158b48$PqFPn9f/ojYdRcu.TVsdKeeRHKwbWApdEypn6wlUQn5"));

    }
}
