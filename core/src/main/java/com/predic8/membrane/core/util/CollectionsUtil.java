package com.predic8.membrane.core.util;

import java.util.*;
import java.util.stream.*;

public class CollectionsUtil {

    public static <T> List<T> concat(List<T> l1, List<T> l2) {
        return Stream.of(l1,l2).filter(Objects::nonNull).flatMap(Collection::stream).toList();
    }
}