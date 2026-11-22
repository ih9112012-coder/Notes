package com.example.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.notes.util.AppExecutors;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests verifying AppExecutors execution on background and main thread fallbacks.
 */
public class AppExecutorsTest {

    @Test
    public void testGetInstanceNotNull() {
        AppExecutors executors = AppExecutors.getInstance();
        assertNotNull(executors);
        assertNotNull(executors.diskIO());
        assertNotNull(executors.mainThread());
    }

    @Test
    public void testDiskIOExecutesTask() throws InterruptedException {
        AppExecutors executors = AppExecutors.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean(false);

        executors.diskIO().execute(() -> {
            ran.set(true);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(ran.get());
    }

    @Test
    public void testMainThreadFallbackInJvm() {
        AppExecutors executors = AppExecutors.getInstance();
        AtomicBoolean ran = new AtomicBoolean(false);

        // In JVM test environments where Looper is not mocked, fallback runs directly
        executors.mainThread().execute(() -> ran.set(true));

        assertTrue(ran.get());
    }
}
