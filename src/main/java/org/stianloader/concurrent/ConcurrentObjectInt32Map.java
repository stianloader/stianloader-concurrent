package org.stianloader.concurrent;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;

public class ConcurrentObjectInt32Map<@NotNull K> implements Object2IntMap<K> {

    private static final long VALUE_MASK_VALUE = (1L << 32) - 1;
    private static final long VALUE_MASK_READ_ACCESS = 1L << 32;
    private static final long VALUE_MASK_READ_LOCKS = 0xFFFF_FFFE_0000_0000L;
    private static final long VALUE_READ_MASK = ConcurrentObjectInt32Map.VALUE_MASK_VALUE | ConcurrentObjectInt32Map.VALUE_MASK_READ_ACCESS;
    private static final long VALUE_MASK_READ_LOCKS_LSB = 0x0000_0002_0000_0000L; // (or 1L << 33)

    @SuppressWarnings("rawtypes") // Otherwise not realizable
    static final AtomicIntegerFieldUpdater<Bucket> BUCKET_SIZE = AtomicIntegerFieldUpdater.newUpdater(Bucket.class, "size");
    @SuppressWarnings("rawtypes") // Otherwise not realizable
    static final AtomicIntegerFieldUpdater<Bucket> BUCKET_CTRL = AtomicIntegerFieldUpdater.newUpdater(Bucket.class, "ctrl");

    final Bucket<K>[] buckets;
    final int bucketCount;

    static final class Bucket<@NotNull T> {
        volatile int ctrl;
        volatile AtomicReferenceArray<T> keys;
        volatile AtomicLongArray values;
        volatile int size;

        private final void lockCtrl() {
            int ctrl;
            while (!ConcurrentObjectInt32Map.BUCKET_CTRL.compareAndSet(this, ctrl = this.ctrl, -ctrl - 1));
            while (this.ctrl != -1) { // Warning: Do not optimise this call to `< 0` !!
                Thread.yield();
            }
        }

        private final void decrementCtrl() {
            int ctrl;
            while (!ConcurrentObjectInt32Map.BUCKET_CTRL.compareAndSet(this, ctrl = this.ctrl, ctrl < 0 ? ctrl + 1 : ctrl - 1));
        }

        private final void incrementCtrl() {
            int ctrl;
            do {
                while ((ctrl = this.ctrl) < 0) {
                    Thread.yield();
                }
            } while (!ConcurrentObjectInt32Map.BUCKET_CTRL.compareAndSet(this, ctrl, ctrl + 1));
        }

        boolean containsValue(int value) {
            AtomicLongArray values = this.values;
            if (values == null) {
                return false;
            }
            long witnessValue = value | ConcurrentObjectInt32Map.VALUE_MASK_READ_ACCESS;
            int index = values.length();
            while (index-- != 0) {
                if ((values.get(index) & (ConcurrentObjectInt32Map.VALUE_MASK_VALUE | ConcurrentObjectInt32Map.VALUE_MASK_READ_ACCESS)) == witnessValue) {
                    return true;
                }
            }
            return false;
        }

        boolean containsKey(T key) {
            AtomicReferenceArray<T> keys = this.keys;
            if (keys == null) {
                return false;
            }
            int index = keys.length();
            while (index-- != 0) {
                if (Objects.equals(keys.get(index), key)) {
                    return true;
                }
            }
            return false;
        }

        int get(T key) {
            AtomicReferenceArray<T> keys = this.keys;
            AtomicLongArray values = this.values;
            if (keys == null) {
                throw new NoSuchElementException("No value for key: " + Objects.toString(key));
            }

            int index = keys.length();
            while (index-- != 0) {
                if (!ConcurrentObjectInt32Map.Bucket.incrementReadCounter(values, index)) {
                    // No readable value in slot
                    continue;
                }

                if (Objects.equals(keys.get(index), key)) {
                    return (int) (ConcurrentObjectInt32Map.Bucket.decrementReadCounter(values, index) & ConcurrentObjectInt32Map.VALUE_MASK_VALUE);
                } else {
                    // Some other value most likely
                    ConcurrentObjectInt32Map.Bucket.decrementReadCounter(values, index);
                }
            }

            throw new NoSuchElementException("No value for key: " + Objects.toString(key));
        }

        boolean put(T key, int value) {
            // FIXME: The entire #put() method is complete bogus.
            // Not only does it not compile, it has the inherent issue of
            // the fact that keys most likely cannot be reliably CASed under the current architecture.
            // Thus, this method would need to get rewritten.
            // In the meantime I shall put this small exercise to rest until I take more time pondering about this issue.
            // However, I believe this architecture works for Int32 -> Object maps.
            this.incrementCtrl();
            AtomicReferenceArray<T> keys = this.keys;
            AtomicLongArray values = this.values;
            if (keys == null) {
                this.decrementCtrl();
                this.growArrays(keys, values);
                return this.put(key, value);
            }


            int len = ConcurrentObjectInt32Map.BUCKET_SIZE.incrementAndGet(this);
            if (len >= values.length()) {
                this.decrementCtrl();
                this.growArrays(keys, values);
                ConcurrentObjectInt32Map.BUCKET_SIZE.decrementAndGet(this);
                return this.put(key, value);
            }

            int index = values.length();
            int storeIndex = -1;
            while (index-- != 0) {
                if (storeIndex < 0) {
                    if (keys.compareAndSet(index, null, key)) {
                        storeIndex = index;
                    }
                } else {
                    
                }
                
                
                // increment read counter before doing the update
                // (we do not need to care about currently ongoing reads, so this is perfectly safe way to go about it)
                // the main reason this is needed is because we need to ensure that the value is not removed
                // in the interim - everything else is fine
                if (!ConcurrentObjectInt32Map.Bucket.incrementReadCounter(values, storeIndex)) {
                    
                }
                
                
                
                
                if (storeIndex == -1 && values.compareAndSet(index, 0, element)) {
                    storeIndex = index;
                } else {
                    if ((values.get(index) & ~ConcurrentInt62Set.CTRL_BIT_READ) == element) {
                        if (storeIndex >= 0) {
                            values.set(storeIndex, 0);
                        }
                        ConcurrentInt62Set.BUCKET_SIZE.decrementAndGet(this);
                        this.decrementCtrl();
                        return false;
                    }
                }
            }

            if (storeIndex >= 0) {
                if (!values.compareAndSet(storeIndex, element, element | CTRL_BIT_READ)) {
                    ConcurrentInt62Set.BUCKET_SIZE.decrementAndGet(this);
                    this.decrementCtrl();
                    throw new AssertionError("Unable to CAS back to read-enabled (but not write-disabled) state.");
                }
                this.decrementCtrl();
                return true;
            }

            ConcurrentInt62Set.BUCKET_SIZE.decrementAndGet(this);
            this.decrementCtrl();
            return this.add(element);
        }

        @CheckReturnValue
        private static final boolean incrementReadCounter(AtomicLongArray array, int index) {
            // We ignore the possibility of an overflow as even when 2^30 threads try to access the same
            // value (an event only likely to occur in enterprise environments), an overflow is the least of all problems,
            // as performance would crawl to a standstill in that case as every thread would race to acquire a lock.
            // Problem being that at most one thread can acquire or relinquish the lock per two CPU cycles at the very minimum
            // (an estimation that is completely wrong given that CAS operations take much longer than one CPU cycle).
            // By the time a reasonable number of threads went through this lock, these threads will have completed
            // the operation they acquired the lock for, and will relinquish the lock. Hence, we can expect
            // an equilibrium between threads acquiring and threads relinquishing the lock, meaning that it isn't
            // possible to reliably oversaturate the lock even in the offhand scenario where a system with these
            // quantities of threads exist.
            // As there are fewer operations involved in relinquishing the read lock compared to the amount of operations
            // involved in acquiring the lock, it can be estimated that there is a bias towards lock-relinquishing CAS
            // operations succeeding. Under this assumption, it becomes impossible that several million threads
            // have the lock acquired at once (except in the case of Objects#equals taking a very long time or when that
            // operation fails or deadlocks).
            long value;
            do {
                value = array.get(index);
                if ((value & ConcurrentObjectInt32Map.VALUE_MASK_READ_ACCESS) == 0) {
                    return false;
                }
            } while (array.compareAndSet(index, value, value + ConcurrentObjectInt32Map.VALUE_MASK_READ_LOCKS_LSB));
            return true;
        }

        private static final long decrementReadCounter(AtomicLongArray array, int index) {
            // We do not need to check for the read access mask as the read access mask is not meant to be unset
            // if the amount of threads waiting for a thread is not 0. As we aren't supposed to decrement the
            // read counter twice, nor decrement it if we do not hold the read lock, we are free to assume that
            // there is at least one thread waiting.
            long value;
            do {
                value = array.get(index);
            } while (!array.compareAndSet(index, value, value - ConcurrentObjectInt32Map.VALUE_MASK_READ_LOCKS_LSB));
            return value;
        }

        private final synchronized void growArrays(AtomicReferenceArray<T> witnessKeys, AtomicLongArray witnessValues) {
            if (this.keys != witnessKeys || this.values != witnessValues) {
                return;
            }

            if (witnessKeys == null) {
                this.keys = new AtomicReferenceArray<>(16);
                this.values = new AtomicLongArray(16);
                return;
            }

            this.lockCtrl();

            int len = witnessKeys.length();
            AtomicReferenceArray<T> grownKeys = new AtomicReferenceArray<>(len << 1);
            AtomicLongArray grownValues = new AtomicLongArray(len << 1);
            for (int i = 0; i < len; i++) {
                grownKeys.set(i + len, grownKeys.get(i));
                grownValues.set(i + len, witnessValues.get(i));
            }
            this.keys = grownKeys;
            this.values = grownValues;

            ConcurrentObjectInt32Map.BUCKET_CTRL.incrementAndGet(this);
        }
    }

    @Override
    public int getInt(Object key) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void putAll(Map<? extends K, ? extends Integer> m) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public int size() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void defaultReturnValue(int rv) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public int defaultReturnValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public ObjectSet<Entry<K>> object2IntEntrySet() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ObjectSet<K> keySet() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IntCollection values() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean containsValue(int value) {
        // TODO Auto-generated method stub
        return false;
    }
}
