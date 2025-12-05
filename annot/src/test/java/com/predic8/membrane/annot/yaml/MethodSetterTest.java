package com.predic8.membrane.annot.yaml;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.util.GrammarMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodSetterTest {

    public class A {
        public void setA1(B b) {}
        public void setA2(B b) {}
        @MCChildElement
        public void setA3(B b) {}
    }

    public class A2 {
        @MCChildElement
        public void setA1(B b) {}
        @MCChildElement
        public void setA2(B b) {}
    }

    public static class B {}

    public static class C {}

    @Test
    public void dontUseMethodsWithoutChildElementAnnotation() {
        MethodSetter ms = MethodSetter.getMethodSetter(new ParsingContext("foo", null,
                        new GrammarMock().withGlobalElement("b", B.class)),
                A.class, "b");
        assertEquals("setA3", ms.getSetter().getName());
    }

    @Test
    public void multiplePotentialSettersFound() {
        assertThrowsExactly(RuntimeException.class, () -> {
            MethodSetter.getMethodSetter(new ParsingContext("foo", null,
                            new GrammarMock().withGlobalElement("b", B.class)),
                    A2.class, "b");
        });
    }

    @Test
    public void noPotentialSetterFound() {
        assertThrowsExactly(RuntimeException.class, () -> {
            MethodSetter.getMethodSetter(new ParsingContext("foo", null,
                            new GrammarMock().withGlobalElement("c", C.class)),
                    A2.class, "c");
        });
    }

}