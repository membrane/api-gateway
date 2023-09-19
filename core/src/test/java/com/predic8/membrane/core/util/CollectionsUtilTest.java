package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CollectionsUtilTest {

    @Test
    void normal() {
        assertEquals(List.of(1,2,3), CollectionsUtil.concat(List.of(1),List.of(2,3)));
    }

    @Test
    void l1Null() {
        assertEquals(List.of(2,3), CollectionsUtil.concat(null,List.of(2,3)));
    }

    @Test
    void l2Null() {
        assertEquals(List.of(1), CollectionsUtil.concat(List.of(1),null));
    }

    @Test
    void allNull() {
        assertEquals(List.of(), CollectionsUtil.concat(null,null));
    }
}