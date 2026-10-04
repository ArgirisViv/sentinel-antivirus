package com.sentinelav.desktop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class EngineClient implements AutoCloseable {
    private final ExecutorService outputReader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sentinel-engine-output");
        thread.setDaemon(true);
        return thread;
    });

    private Process activeProcess;

    synchronized void start(
            List<String> command,
            Consumer<String> output,
            BiConsumer<Integer, Exception> completion) throws IOException {
        if (activeProcess != null && activeProcess.isAlive()) {
            throw new IllegalStateException("A Sentinel AV operation is already running.");
        }

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        activeProcess = process;

        try {
            outputReader.execute(() -> {
                int exitCode = -1;
                Exception failure = null;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.accept(line);
                    }
                    exitCode = process.waitFor();
                } catch (IOException | InterruptedException error) {
                    failure = error;
                    if (error instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                } catch (RuntimeException error) {
                    failure = error;
                } finally {
                    synchronized (EngineClient.this) {
                        if (activeProcess == process) {
                            activeProcess = null;
                        }
                    }
                    completion.accept(exitCode, failure);
                }
            });
        } catch (RuntimeException error) {
            activeProcess = null;
            process.destroyForcibly();
            throw error;
        }
    }

    synchronized boolean isRunning() {
        return activeProcess != null && activeProcess.isAlive();
    }

    synchronized void stop() {
        if (activeProcess != null && activeProcess.isAlive()) {
            activeProcess.destroy();
        }
    }

    @Override
    public synchronized void close() {
        if (activeProcess != null && activeProcess.isAlive()) {
            activeProcess.destroyForcibly();
        }
        outputReader.shutdownNow();
    }
}
