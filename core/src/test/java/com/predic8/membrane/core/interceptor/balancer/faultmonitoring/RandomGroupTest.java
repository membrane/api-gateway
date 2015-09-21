package com.predic8.membrane.core.interceptor.balancer.faultmonitoring;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Fabian Kessler / Optimaize
 *         This class was copy-pasted from the internal crema-collections library, and retro-fitted for Java6
 *         and to use Junit instead of TestNG and without AssertJ.
 */
public class RandomGroupTest {


    /**
     * Single entry
     */
    @Test
    public void testSingleEntry() throws Exception {
        RandomGroup<String> randomGroup = new RandomGroup<String>();
        randomGroup.add("foo", 1d);
        for (int i=0; i<1000; i++) {
            assertEquals(randomGroup.next(), "foo");
        }
    }

    /**
     * Putting in only 3 items and making sure we can get out 1000.
     */
    @Test
    public void testEndlessNext() throws Exception {
        RandomGroup<Integer> randomGroup = new RandomGroup<Integer>();
        for (int i=1; i<=3; i++) {
            randomGroup.add(i, 1d);
        }
        for (int i=0; i<1000; i++) {
            randomGroup.next();
        }
    }

    /**
     * "foo" has weight 1.0 while "bar" has weight 0.1 and therefore foo appears 10x more often.
     */
    @Test
    public void testWeight() throws Exception {
        RandomGroup<String> randomGroup = new RandomGroup<String>();
        randomGroup.add("foo", 1d);
        randomGroup.add("bar", 0.1d);
        int fooCounter = 0;
        int barCounter = 0;
        for (int i=0; i<1000; i++) {
            String next = randomGroup.next();
            if (next.equals("foo")) {
                fooCounter++;
            } else {
                barCounter++;
            }
        }
        assertEquals(fooCounter, barCounter * 10, 300); //had to increase the delta quite a lot...
    }


    /**
     * Weight 0d is not permitted.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNotZero() throws Exception {
        new RandomGroup<Object>().add(new Object(), 0d);
    }


    /**
     * Fetching before putting in.
     */
    @Test(expected = IllegalStateException.class)
    public void testEmpty() throws Exception {
        new RandomGroup().next();
    }



    /**
     */
    @Test
    public void testToList() throws Exception {
        RandomGroup<String> randomGroup = new RandomGroup<String>();
        randomGroup.add("foo", 1d);
        randomGroup.add("bar", 0.1d);

        List<String> strings = randomGroup.toList();
        expectFooAndBar(strings);

        BoolCounter boolCounter = new BoolCounter();
        for (int i=0; i<1000; i++) {
            strings = randomGroup.toList();
            expectFooAndBar(strings);
            boolCounter.count(strings.get(0).equals("foo"));
        }
        //the one with higher chance comes mostly as first item:
        assertEquals(boolCounter.getTrue(), boolCounter.getFalse(), 300);
        //but both happen to come first, order is not strict:
        assertTrue(boolCounter.getTrue() >= 1);
        assertTrue(boolCounter.getFalse() >= 1);

    }

    private void expectFooAndBar(List<String> strings) {
        assertEquals(strings.size(), 2);
        assertTrue(strings.contains("foo"));
        assertTrue(strings.contains("bar"));
    }


    @Test
    public void testAddingSameMultipleTimes() throws Exception {
        RandomGroup<Character> selector = RandomGroup.<Character>create()
                .add('A', 1d)
                .add('B', 2d)
                .add('A', 7d); //A again in purpose

        int a = 0;
        int b = 0;
        for (int i=0; i<1000; i++) {
            char chr = selector.next();
            switch (chr) {
                case 'A':
                    a++; break;
                case 'B':
                    b++; break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        //the assertions are very generous, in practice the results are much much closer to the expected theoretical best cases.
        assertTrue(a >= 700 && a <= 900);
        assertTrue(b >= 150 && b <= 250);
    }

    @Test
    public void testStresstest() throws Exception {
        RandomGroup<Character> selector = RandomGroup.<Character>create()
                .add('A', 0.7d)
                .add('B', 0.2d)
                .add('C', 0.1d);

        int a = 0;
        int b = 0;
        int c = 0;
        for (int i=0; i<1000000; i++) {
            char chr = selector.next();
            switch (chr) {
                case 'A':
                    a++; break;
                case 'B':
                    b++; break;
                case 'C':
                    c++;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        System.out.println("A: "+a);
        System.out.println("B: "+b);
        System.out.println("C: "+c);

        //the assertions are very generous, in practice the results are much much closer to the expected theoretical best cases.
        assertTrue(a >= 650000 && a <= 750000);
        assertTrue(b >= 170000 && b <= 230000);
        assertTrue(c >= 80000 && c <= 120000);
    }



    private static class BoolCounter {
        private long countTrue = 0L;
        private long countFalse = 0L;

        public void count(boolean b) {
            if(b) {
                ++this.countTrue;
            } else {
                ++this.countFalse;
            }

        }

        public long getTrue() {
            return this.countTrue;
        }

        public long getFalse() {
            return this.countFalse;
        }
    }


}