package com.predic8.membrane.core.interceptor.shadowing;

import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ShadowingInterceptorTest {

    @Mock
    private Target mockTarget;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetDestFromTarget_WithUrl() {
        when(mockTarget.getUrl()).thenReturn("http://example.com");
        String result = ShadowingInterceptor.getDestFromTarget(mockTarget, "/path");
        assertEquals("http://example.com", result);
    }

    @Test
    void testGetDestFromTarget_WithoutUrl() {
        when(mockTarget.getHost()).thenReturn("localhost");
        when(mockTarget.getPort()).thenReturn(8080);
        String result = ShadowingInterceptor.getDestFromTarget(mockTarget, "/path");
        assertEquals("http://localhost:8080/path", result);
    }

}
