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