package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

public class RequestTest {

    Request r1 = Request.get().path("/shop/products/");

    @Test
    public void ajustPathAccordingToBasePath() {
        r1.ajustPathAccordingToBasePath("/shop/");
        Assert.assertEquals("/products/", r1.getPath());
    }


}