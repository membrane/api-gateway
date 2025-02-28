package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.test.*;
import com.predic8.schema.*;
import com.predic8.wsdl.*;
import groovy.namespace.*;
import org.junit.jupiter.api.*;

public class TestCreatorTest {

    public static final String MATERIAL_SCHEMA_NS = "http://predic8.com/wsdl/material/ArticleService/1/";

    @Test
    void foo() {

        var tc = new TestCreator();
        var tcc = new TestCreatorContext();



        var def = new WSDLParser().parse(TestUtil.getPathFromResource("/validation/ArticleService.wsdl"));
        var e = def.getElement(new QName(MATERIAL_SCHEMA_NS, "create"));

        System.out.println("e = " + e);
        
        tc.createElement(e,tcc);

        System.out.println(tcc.sb);
        
    }

    @Test
    void xsd() {
        var xsd = new SchemaParser().parse(TestUtil.getPathFromResource("/xml/xsd/nested-elements.xsd"));

        var tc = new TestCreator();
        var tcc = new TestCreatorContext();

        tc.createElement(xsd.getElement("person"), tcc);
        System.out.println(tcc.sb);
    }
}
