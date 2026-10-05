package com.sentinelav.desktop;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineClientTest {
    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    private static List<String> shell(String windowsCommand, String unixCommand) {
        return WINDOWS
                ? List.of("cmd.exe", "/c", windowsCommand)
                : List.of("sh", "-c", unixCommand);
    }

    @Test
    void capturesChildOutputAndExitCode() throws Exception {
        try (EngineClient client = new EngineClient()) {
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<String> output = new AtomicReference<>();
            AtomicReference<Exception> failure = new AtomicReference<>();
            AtomicInteger exitCode = new AtomicInteger(-1);

            client.start(
                    shell("echo sentinel-engine-test", "echo sentinel-engine-test"),
                    output::set,
                    (code, error) -> {
                        exitCode.set(code);
                        failure.set(error);
                        completed.countDown();
                    });

            assertTrue(completed.await(10, TimeUnit.SECONDS), "Child process did not complete.");
            assertEquals(0, exitCode.get());
            assertNull(failure.get());
            assertEquals("sentinel-engine-test", output.get().trim());
        }
    }

    @Test
    void rejectsConcurrentEngineOperationAndStopsActiveChild() throws Exception {
        try (EngineClient client = new EngineClient()) {
            CountDownLatch completed = new CountDownLatch(1);
            client.start(
                    shell("ping -n 20 127.0.0.1 > nul", "exec sleep 20"),
                    ignored -> { },
                    (code, failure) -> completed.countDown());

            assertTrue(client.isRunning(), "Long-running test process should be active.");
            assertThrows(
                    IllegalStateException.class,
                    () -> client.start(
                            shell("exit 0", "exit 0"),
                            ignored -> { },
                            (code, failure) -> { }));

            client.stop();
            assertTrue(
                    completed.await(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS),
                    "Stopped child process did not complete.");
            assertFalse(client.isRunning());
        }
    }

    @Test
    void reportsMissingExecutableAsStartFailure() {
        try (EngineClient client = new EngineClient()) {
            assertThrows(
                    IOException.class,
                    () -> client.start(
                            List.of("sentinel-av-file-that-does-not-exist.exe"),
                            ignored -> { },
                            (code, failure) -> { }));
            assertFalse(client.isRunning());
        }
    }
}
