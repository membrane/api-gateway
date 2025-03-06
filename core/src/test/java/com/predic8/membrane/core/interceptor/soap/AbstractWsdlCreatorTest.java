package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.test.*;
import com.predic8.wsdl.*;
import org.junit.jupiter.api.*;

public class AbstractWsdlCreatorTest {

    @Test
    void createWsdl() {

        WsdlCreator<WsdlCreatorContext> creator = new AbstractWsdlCreator() {};

        var def = new WSDLParser().parse(TestUtil.getPathFromResource("/ws/cities.wsdl"));
        var ctx = new WsdlCreatorContext();
        creator.createDefinitions(def,ctx);

    }

}