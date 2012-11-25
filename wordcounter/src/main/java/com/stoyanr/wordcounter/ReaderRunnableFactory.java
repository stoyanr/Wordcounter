package com.stoyanr.wordcounter;

import static com.stoyanr.util.Logger.debug;
import static com.stoyanr.util.Logger.isDebug;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;

final class ReaderRunnableFactory implements RunnableFactory {

    private static final int MAX_ST_SIZE = 1024 * 1024;

    private static final EnumSet<FileVisitOption> OPTIONS = EnumSet
        .of(FileVisitOption.FOLLOW_LINKS);

    private final File file;
    private final BlockingQueue<String> queue;
    private final WordCounter wc;

    ReaderRunnableFactory(File file, BlockingQueue<String> queue, WordCounter wc) {
        this.file = file;
        this.queue = queue;
        this.wc = wc;
    }

    @Override
    public Runnable getRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                read();
            }
        };
    }

    private void read() {
        try {
            if (file.isDirectory()) {
                readDirectory(Paths.get(file.getPath()));
            } else {
                readFile(Paths.get(file.getPath()));
            }
        } catch (IOException e) {
        }
    }

    private void readDirectory(Path dir) throws IOException {
        FileProcessor fp = new FileProcessor() {
            @Override
            public void process(Path file) throws IOException {
                readFile(file);
            }
        };
        Files.walkFileTree(dir, OPTIONS, Integer.MAX_VALUE, new WordCounterVisitor(fp));
    }

    private void readFile(final Path file) throws IOException {
        try {
            if (Files.size(file) <= MAX_ST_SIZE) {
                String text = FileUtils.readFileToString(file);
                logReaderJobDone(file, text);
                produceText(text);
            } else {
                FileUtils.readFileAsync(file, new TextProcessor<String>() {
                    @Override
                    public String process(String text, String state) throws InterruptedException {
                        int ei = wc.getEndIndex(text);
                        String rem = (state != null)? state : "";
                        String textx = rem + text.substring(0, ei);
                        rem = text.substring(ei);
                        logReaderJobDone(file, textx);
                        produceText(textx);
                        return rem;
                    }
                });
            }
        } catch (InterruptedException e) {
        }
    }

    private void produceText(String text)
        throws InterruptedException {
        long t0 = logReaderQueueFull();
        queue.put(text);
        logReaderWaitTime(t0);
    }

    private long logReaderQueueFull() {
        long t0 = 0;
        if (isDebug() && queue.remainingCapacity() == 0) {
            debug("[Reader (%s)] Queue full, waiting ...", getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private void logReaderWaitTime(long t0) {
        if (isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            debug("[Reader (%s)] Waited for %.2f us", getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private void logReaderJobDone(Path file, String text) {
        if (isDebug()) {
            debug("[Reader (%s)] Read text '%s' (%s)", getThreadName(), trim(text), file.toString());
        }
    }

    private static String trim(String text) {
        return text.substring(0, Math.min(text.length(), 20));
    }

    private static String getThreadName() {
        return Thread.currentThread().getName();
    }

}
