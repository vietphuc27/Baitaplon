package client.controller;

import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class JavaFxTestSupport {
    private static boolean startupAttempted;
    private static Throwable startupError;

    static void ensureJavaFxAvailable() {
        synchronized (JavaFxTestSupport.class) {
            if (!startupAttempted) {
                startupAttempted = true;
                CountDownLatch latch = new CountDownLatch(1);
                try {
                    Platform.startup(latch::countDown);
                    assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit did not start in time");
                } catch (IllegalStateException alreadyStarted) {
                    startupError = null;
                } catch (Throwable t) {
                    startupError = t;
                }
            }
        }

        Assumptions.assumeTrue(startupError == null,
                () -> "JavaFX toolkit is not available in this environment: " + startupError);
    }

    static <T> T runOnFxThread(Callable<T> action) throws Exception {
        ensureJavaFxAvailable();
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX action did not finish in time");
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return result.get();
    }

    static void flushFxEvents() throws Exception {
        runOnFxThread(() -> null);
    }
}
