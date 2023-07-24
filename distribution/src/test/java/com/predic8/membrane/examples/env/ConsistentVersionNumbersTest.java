package com.predic8.membrane.examples.env;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.examples.env.ConsistentVersionNumbers.getConstantsVersionPattern;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsistentVersionNumbersTest {

    @Test
    public void constantsVersionRegexPatternMatchTest() {
        assertTrue(getConstantsVersionPattern().matcher("String version = \"5.2\";").find());
    }

    @Test
    public void constantsVersionRegexPatternNoMatchTest() {
        assertTrue(!getConstantsVersionPattern().matcher("String version = \"5\";").find());
    }
}
