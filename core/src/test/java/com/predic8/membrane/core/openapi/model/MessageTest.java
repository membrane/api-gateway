package com.predic8.membrane.core.openapi.model;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.AssertTrue;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTest {

    private Request request;

    @BeforeEach
    void setup() throws URISyntaxException {
        request = Request.post().json().path("/star-star").body("{}");
    }

    @Test
    void starStarTest() {
        assertTrue(request.isOfMediaType("*/*"));
    }

    @Test
    void typeStarTest() {
        assertTrue(request.isOfMediaType("application/*"));
    }

    @Test
    void starTypeTest() {
        assertFalse(request.isOfMediaType("*/json"));
    }
}