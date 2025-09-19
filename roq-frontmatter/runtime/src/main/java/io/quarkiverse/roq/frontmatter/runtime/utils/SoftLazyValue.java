package io.quarkiverse.roq.frontmatter.runtime.utils;

import java.lang.ref.SoftReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Lazy Value, which are cleared at the discretion of the garbage collector in response to memory demand.
 */
public class SoftLazyValue<T> {

    private final Supplier<T> supplier;

    private final Lock lock = new ReentrantLock();

    private transient volatile SoftReference<T> ref = new SoftReference<>(null);

    public SoftLazyValue(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public T get() {
        SoftReference<T> currentRef = ref;
        T value = currentRef == null ? null : currentRef.get();
        if (value != null) {
            return value;
        }

        lock.lock();
        try {
            currentRef = ref;
            value = currentRef == null ? null : currentRef.get();
            if (value == null) {
                value = supplier.get();
                ref = new SoftReference<>(value);
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    public T getIfPresent() {
        SoftReference<T> currentRef = ref;
        return currentRef == null ? null : currentRef.get();
    }

    public void clear() {
        lock.lock();
        try {
            ref = null;
        } finally {
            lock.unlock();
        }
    }

}
