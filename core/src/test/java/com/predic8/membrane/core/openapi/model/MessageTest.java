package com.predic8.membrane.core.openapi.model;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.AssertTrue;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTest {

    @Test
    void starStarTest() throws URISyntaxException {
        Request request = Request.post().json().path("/star-star").body("{}");
        assertTrue(request.isOfMediaType("*/*"));
    }

    @Test
    void typeStarTest() throws URISyntaxException {
        Request request = Request.post().json().path("/star-star").body("{}");
        assertTrue(request.isOfMediaType("application/*"));
    }

    @Test
    void starTypeTest() throws URISyntaxException {
        Request request = Request.post().json().path("/star-star").body("{}");
        assertFalse(request.isOfMediaType("*/json"));
    }
}