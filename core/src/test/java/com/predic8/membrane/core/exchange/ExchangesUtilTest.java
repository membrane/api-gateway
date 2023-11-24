/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

class ExchangesUtilTest {

    Exchange exchange;

    @BeforeEach
    void setUp() {
        exchange = new Request.Builder().buildExchange();
    }

    @Test
    void getTime() {
        var cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2023);
        cal.set(Calendar.MONTH, 9);
        cal.set(Calendar.DAY_OF_MONTH, 5);
        cal.set(Calendar.HOUR_OF_DAY, 11);
        cal.set(Calendar.MINUTE, 10);
        cal.set(Calendar.SECOND, 30);
        cal.set(Calendar.MILLISECOND, 190);

        exchange.setTime(cal);

        assertEquals("2023-10-05 11:10:30.190", ExchangesUtil.getTime(exchange));

        exchange.setTime(null);

        assertEquals(Constants.UNKNOWN, ExchangesUtil.getTime(exchange));
    }
}