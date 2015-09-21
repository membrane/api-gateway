package com.predic8.membrane.core.interceptor.balancer.faultmonitoring;

import java.util.*;

/**
 * A group of items that can be fetched randomly, endlessly.
 *
 * <p>This class is not thread safe if modifications are made using add(). The general concept is to construct one,
 * add() all items, and then only use next(). In this way multiple threads can call next() simultaneously, no problem.</p>
 *
 * <p>This class does not implement Collection because iteration can't be defined nicely. Either the items are
 * iterated just once in order, which is totally against the use of this class. Or iteration is random and endless,
 * which is completely against the Collection interface and usage patterns.<br/>
 * This is a special purpose container of items, and won't be passed around like a Collection. It should only
 * be used locally. If one wants to pass around the items within then convert to a standard Collection.</p>
 *
 * @author based on http://stackoverflow.com/questions/6409652/random-weighted-selection-java-framework
 * @author Fabian Kessler / Optimaize
 *         This class was copy-pasted from the internal crema-collections library, and retro-fitted for Java6.
 */
class RandomGroup<E> {

    private final NavigableMap<Double, E> map = new TreeMap<Double, E>();
    private final Random random;
    private double total = 0;

    public static <E> RandomGroup<E> create() {
        return new RandomGroup<E>();
    }
    public RandomGroup() {
        this(new Random());
    }
    public RandomGroup(Random random) {
        this.random = random;
    }

    /**
     * @param item The same item may be added multiple times. Each addition increases its chance by the given weight.
     * @param weight Must be a number > 0. The higher the weight, the better the chance.
     *               If you want to give all items the same chance then use weight = 1.0d
     * @throws IllegalArgumentException if weight is smaller or equal 0
     */
    public RandomGroup add(E item, double weight) {
        if (weight <= 0d) {
            throw new IllegalArgumentException("Weight must be >0 but was: "+weight);
        }
        total += weight;
        map.put(total, item);
        return this;
    }

    /**
     * Retrieves an item from the group randomly.
     *
     * <p>This group never runs out of items because it just returns one each time, even if that item was given
     * out already. <br/>
     * Also, there is no logic to not return the same item twice in a row. If you need that kind of functionality then
     * maybe just call the method repeatedly until you get a new item. (Remember that the group may be constructed with
     * just one item, but that's up to you, you feed it...)
     * </p>
     *
     * @throws java.lang.IllegalStateException if the group is empty (add() was never called).
     */
    public E next() {
        if (map.isEmpty()) {
            throw new IllegalStateException("Group is empty!");
        }
        double value = random.nextDouble() * total;
        return map.ceilingEntry(value).getValue();
    }

    /**
     * Returns each item once, in random order, considering each item's weight.
     * Therefore those with higher weight have a better chance to appear early.
     *
     * This can be used as alternative to next() when you may want to skip the first and see what else there is.
     * Because next() can return the same more than once.
     *
     * WARNING code not optimized for many items.
     */
    public List<E> toList() {
        List<E> ret = new ArrayList<E>(map.size());

        //this code is acceptable for small groups, but needs a rewrite for larger.
        RandomGroup<E> copy = copy(map, random);
        while (!copy.isEmpty()) {
            double value = copy.random.nextDouble() * copy.total;
            Double key = copy.map.ceilingKey(value);
            E remove = copy.map.remove(key);
            ret.add(remove);
            if (copy.map.isEmpty()) break;
            copy = copy(copy.map, copy.random);
        }

        return ret;
    }

    private RandomGroup<E> copy(NavigableMap<Double, E> map, Random random) {
        RandomGroup<E> copy = new RandomGroup<E>(random);
        for (Map.Entry<Double, E> entry : map.entrySet()) {
            copy.add(entry.getValue(), entry.getKey());
        }
        return copy;
    }

    public E nextOrNull() {
        if (map.isEmpty()) {
            return null;
        }
        double value = random.nextDouble() * total;
        return map.ceilingEntry(value).getValue();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

}
