package org.stianloader.tests.concurrent;

import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.stianloader.concurrent.ConcurrentInt62Set;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

@TestMethodOrder(OrderAnnotation.class)
public class Int62SetTests {

    private static final void rangeInsert(LongSet set, int from, int to) {
        assert from <= to;
        while (from != to) {
            set.add(from++);
        }
    }

    private static final void rangeRemove(LongSet set, int from, int to) {
        assert from <= to;
        while (from != to) {
            set.remove(from++);
        }
    }

    private static final void rangeGuardedRemove(LongSet set, int from, int to) {
        assert from <= to;
        while (from != to) {
            if (!set.remove(from++)) {
                assertTrue(false, "Guarded removal did not remove object " + (from - 1));
            }
        }
    }

    @Test
    @Order(value = -100)
    public void synchronousLargeInsertionTest() {
        LongSet set = new ConcurrentInt62Set(1 << 16);
        for (int i = 0; i < (1 << 10); i++) {
            assertFalse(set.contains(i), "Contains mismatch before insertion");
            assertTrue(set.add(i), "Insertion feedback value mismatch");
            assertTrue(set.contains(i), "Contains mismatch after insertion");
        }
        for (int i = 0; i < (1 << 10); i++) {
            assertTrue(set.contains(i), "Contains mismatch");
        }
        assertEquals(1 << 10, set.size(), "Set size mismatch");
    }

    @Order(value = -110)
    @RepeatedTest(value = 4, failureThreshold = 1)
    public void asynchronousSmallInsertionTest() {
        LongSet set = new ConcurrentInt62Set(1);
        CompletableFuture<?>[] futures = new CompletableFuture[16];
        for (int i = 0; i < 16; i++) {
            final int sect = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                rangeInsert(set, sect << 8, (sect + 1) << 8);
            });
        }
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures).get();
        });
        assertEquals(16 << 8, set.size(), "Set size mismatch");
        assertFalse(set.isEmpty(), "Set should not be empty");
        {
            LongIterator it = set.iterator();
            int size = 0;
            LongSet witness = new LongOpenHashSet();
            while (it.hasNext()) {
                assertTrue(witness.add(it.nextLong()), "Iterator must return unique (non-duplciate) values");
                size++;
            }
            assertEquals(set.size(), size, "Iterated object count must match the set's reported size");
        }
        for (long i = 0; i < ((long) 16) << 8; i++) {
            if (!set.contains(i)) {
                assertTrue(set.contains(i), "Element should be contained in set: " + i);
            }
        }
    }

    @Test
    public void emptySetTest() {
        assertTrue(new ConcurrentInt62Set(8).isEmpty());
        assertFalse(new ConcurrentInt62Set(8).iterator().hasNext());
    }

    @RepeatedTest(value = 4, failureThreshold = 1)
    public void asynchronousSmallInsertAndRemoveTest() {
        LongSet set = new ConcurrentInt62Set(1);
        CompletableFuture<?>[] futures = new CompletableFuture[16];
        for (int i = 0; i < 16; i++) {
            final int sect = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                rangeInsert(set, sect << 8, (sect + 1) << 8);
            });
        }
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures).get();
        });
        assertEquals(16 << 8, set.size(), "Set size mismatch");
        assertFalse(set.isEmpty(), "Set should not be empty");
        {
            LongIterator it = set.iterator();
            int size = 0;
            LongSet witness = new LongOpenHashSet();
            while (it.hasNext()) {
                assertTrue(witness.add(it.nextLong()), "Iterator must return unique (non-duplciate) values");
                size++;
            }
            assertEquals(set.size(), size, "Iterated object count must match the set's reported size");
        }
        for (int i = 0; i < 16; i++) {
            final int sect = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                rangeGuardedRemove(set, sect << 8, (sect + 1) << 8);
            });
        }
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures).get();
        });
        assertEquals(0, set.size(), "Set size expected empty");
        assertTrue(set.isEmpty(), "Set should be empty");
        assertFalse(set.iterator().hasNext(), "Iterator expected to not indicate a next element for an empty set.");
    }

    @Test
    public void asynchronousInsertionTest() {
        final int precisionDepth = 128;

        LongSet set = new ConcurrentInt62Set(1 << 8);
        CompletableFuture<?>[] futures = new CompletableFuture[precisionDepth];
        int[] sections = new int[precisionDepth];
        for (int i = 0; i < precisionDepth; i++) {
            sections[i] = i;
        }
        Collections.shuffle(Arrays.asList(sections));
        for (int i = 0; i < precisionDepth; i++) {
            final int sect = sections[i];
            futures[i] = CompletableFuture.runAsync(() -> {
                rangeInsert(set, sect << 12, (sect + 1) << 12);
            });
        }
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures).get();
        });
        assertEquals(precisionDepth << 12, set.size(), "Set size mismatch");
        assertFalse(set.isEmpty(), "Set should not be empty");
        for (long i = 0; i < ((long) precisionDepth) << 12; i++) {
            if (!set.contains(i)) {
                assertTrue(set.contains(i), "Element should be contained in set: " + i);
            }
        }
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        for (int i = 0; i < (1 << 10); i++) {
            long v = tlr.nextLong(((long) precisionDepth) << 12, 1L << 62);
            assertFalse(set.contains(v), "Random element " + v + " should not be contained in set!");
        }
        Collections.shuffle(Arrays.asList(sections));
        Arrays.fill(futures, null);
        for (int i = 0; i < precisionDepth; i++) {
            final int sect = sections[i];
            futures[i] = CompletableFuture.runAsync(() -> {
                rangeRemove(set, sect << 12, (sect + 1) << 12);
            });
        }
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures).get();
        });
        assertEquals(0, set.size(), "Set size mismatch (expected empty)");
        assertTrue(set.isEmpty(), "Set should be empty");
    }

    @Test
    public void synchronousContainsTest() {
        LongSet set = new ConcurrentInt62Set(8);
        assertEquals(0, set.size(), "Set must be initialized as an empty set");
        for (int i = 0; i < 10; i++) {
            assertFalse(set.contains(i), "Value " + i + " should not exist within an empty set.");
        }
        for (int i = 0; i < 10; i++) {
            assertFalse(set.contains(i), "Value " + i + " not yet added, but reported as present.");
            set.add(i);
            assertTrue(set.contains(i), "Value " + i + " added, but reported as absent.");
        }
    }

    @Test
    public void synchronousInsertionTest() {
        LongSet set = new ConcurrentInt62Set(8);
        assertEquals(0, set.size(), "Set must be initialized as an empty set");
        for (int i = 0; i < 10; i++) {
            set.add(i);
            assertEquals(i + 1, set.size(), "Set expected to be at " + (i + 1) + " elements");
        }
    }

    @Test
    public void synchronousIterateAndRemoveTest() {
        LongSet set = new ConcurrentInt62Set(8);
        for (int i = 0; i < 10000; i++) {
            set.add(i);
        }

        assertEquals(10000, set.size() , "Set size mismatch");

        LongIterator it = set.iterator();
        while (it.hasNext()) {
            long value = it.nextLong();
            it.remove();
            assertFalse(set.contains(value), "Set still contains value after removal!");
        }

        assertEquals(0, set.size(), "Set expected empty");
        assertTrue(set.isEmpty(), "Set expected to report itself as empty");
    }

    @Test
    public void synchronousIterationTest() {
        LongSet set = new ConcurrentInt62Set(8);
        for (int i = 0; i < 10000; i++) {
            set.add(i);
        }

        assertEquals(10000, set.size(), "Set size mismatch");

        LongIterator it = set.iterator();
        while (it.hasNext()) {
            long val = it.nextLong();
            if (val < 0) {
                assertionFailure()
                    .expected("[0; 10000)")
                    .actual(val)
                    .reason("Value smaller than accepted minimum (0)")
                    .buildAndThrow();
            } else if (val >= 10000) {
                assertionFailure()
                    .expected("[0; 10000)")
                    .actual(val)
                    .reason("Value larger than accepted maximum (9999)")
                    .buildAndThrow();
            }
        }
    }

    @Test
    public void synchronousRandomCollisionLikelyInsertionTest() {
        LongSet set = new ConcurrentInt62Set(8);
        LongSet witness = new LongOpenHashSet();
        assertEquals(0, set.size(), "Set must be initialized as an empty set");
        for (int i = 0; i < 100000; i++) {
            long val = ThreadLocalRandom.current().nextLong(0L, 1L << 10);
            assertEquals(witness.add(val), set.add(val), "Witness did not report a modification while the actual set did report one or vice-versa. For inserting " + val);
        }

        assertEquals(witness.size(), set.size(), "Set must be at " + witness.size() + " elements after inserting 100000 random values with very likely collisions.");
    }

    @Test
    public void synchronousRandomInsertionTest() {
        LongSet set = new ConcurrentInt62Set(8);
        LongSet witness = new LongOpenHashSet();
        assertEquals(0, set.size(), "Set must be initialized as an empty set");
        for (int i = 0; i < 10000; i++) {
            long val = ThreadLocalRandom.current().nextLong(0L, 1L << 62);
            assertEquals(witness.add(val), set.add(val), "Witness did not report a modification while the actual set did report one or vice-versa.");
        }

        assertEquals(witness.size(), set.size(), "Set must be at " + witness.size() + " elements after inserting 10000 random values.");
    }

    @Test
    public void synchronousUncheckedIterateAndRemoveTest() {
        LongSet set = new ConcurrentInt62Set(8);
        for (int i = 0; i < 10000; i++) {
            set.add(i);
        }

        assertEquals(10000, set.size() , "Set size mismatch");

        LongIterator it = set.iterator();
        for (int i = 0; i < 10000; i++) {
            long value = it.nextLong();
            it.remove();
            assertFalse(set.contains(value), "Set still contains value after removal!");
        }

        assertEquals(0, set.size(), "Set expected empty");
        assertTrue(set.isEmpty(), "Set expected to report itself as empty");
    }

    @Test
    public void synchronousUncheckedIterationTest() {
        LongSet set = new ConcurrentInt62Set(8);
        for (int i = 0; i < 10000; i++) {
            set.add(i);
        }

        assertEquals(10000, set.size(), "Set size mismatch");

        LongIterator it = set.iterator();
        for (int i = 0; i < 10000; i++) {
            long val = it.nextLong();
            if (val < 0) {
                assertionFailure()
                    .expected("[0; 10000)")
                    .actual(val)
                    .reason("Value smaller than accepted minimum (0)")
                    .buildAndThrow();
            } else if (val >= 10000) {
                assertionFailure()
                    .expected("[0; 10000)")
                    .actual(val)
                    .reason("Value larger than accepted maximum (9999)")
                    .buildAndThrow();
            }
        }
    }

    @Test
    public void synchronousIterationAndInsertTest() {
        LongSet set = new ConcurrentInt62Set(1);
        LongSet[] witnesses = new LongSet[1000];
        LongIterator[] iterators = new LongIterator[1000];

        for (int i = 0; i < 1000; i++) {
            set.add(i);
            witnesses[i] = new LongOpenHashSet();
            iterators[i] = set.iterator();
            for (int j = 0; j <= i; j++) {
                if (iterators[j].hasNext()) {
                    long value = iterators[j].nextLong();
                    if (!witnesses[j].add(value)) {
                        assertTrue(false, "Did not expect duplicate element '" + value + "' in iterator '" + j + "' after adding '" + i + "'. All values previously returned by the iterator: " + witnesses[j]);
                    }
                    assertFalse(set.add(value), "Insertion of a value returned by an iterator into the set should not succeed.");
                }
            }
        }

        assertFalse(iterators[0].hasNext(), "First iterator should be exhausted by now.");
        assertFalse(iterators[0].hasNext(), "Exhausted should stay exhausted.");
        // Hint: Due to behaviour of the Iterator, the second iterator should also be exhausted,
        // but as this is not in the specification this information is virtually irrelevant.
    }
}
