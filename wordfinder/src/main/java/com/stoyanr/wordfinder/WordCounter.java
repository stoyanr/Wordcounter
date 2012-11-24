/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.stoyanr.wordfinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

class WordCounter {

    public static final String DEFAULT_DELIMITERS = " \t\n\r\f";
    public static final int DEFAULT_NUM_COUNTERS = 4;

    private final String delimiters;
    private final int numCounters;

    public WordCounter() {
        this(DEFAULT_DELIMITERS, DEFAULT_NUM_COUNTERS);
    }

    public WordCounter(String delimiters, int numCounters) {
        this.delimiters = delimiters;
        this.numCounters = numCounters;
    }

    public Map<String, Integer> countWords(String text) {
        if (text == null)
            throw new IllegalArgumentException();
        return countWordsInternal(new StringTokenizer(text, delimiters));
    }

    public Map<String, Integer> countWords(List<String> texts) {
        Map<String, Integer> result = new HashMap<>();
        for (String text : texts) {
            addCounts(result, countWords(text));
        }
        return result;
    }

    private Map<String, Integer> countWordsInternal(StringTokenizer t) {
        assert (t != null);
        Map<String, Integer> result = new HashMap<>();
        while (t.hasMoreTokens()) {
            String word = t.nextToken();
            addCount(result, word, 1);
        }
        return result;
    }

    public Map<String, Integer> countWords(File file) throws IOException {
        if (file == null || !file.exists())
            throw new IllegalArgumentException();
        return (file.isDirectory()) ? countWordsDir(file) : countWordsFile(file);
    }

    private Map<String, Integer> countWordsFile(File file) throws IOException {
        assert (file != null && file.isFile());
        String text = FileUtils.readFileToString(file);
        return countWords(text);
    }

    private Map<String, Integer> countWordsDir(File dir) {
        assert (dir != null && dir.isDirectory());
        ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>(16, 0.75f, numCounters);
        try {
            BlockingQueue<String> queue = new LinkedBlockingQueue<>(numCounters * 3);
            ScheduledExecutorService readers = createReaders(dir, queue);
            ScheduledExecutorService counters = createCounters(queue, counts);
            boolean finished = shutdownReaders(readers);
            assert (finished);
            waitForEmpty(queue);
            boolean terminated = shutdownCounters(counters);
            assert (terminated);
        } catch (InterruptedException e) {
        }
        return counts;
    }

    private ScheduledExecutorService createReaders(File dir, BlockingQueue<String> queue) {
        ScheduledExecutorService readers = new ScheduledThreadPoolExecutor(1);
        readers.submit(new FileReaderRunnable(dir, queue));
        return readers;
    }

    private ScheduledExecutorService createCounters(BlockingQueue<String> queue,
        ConcurrentMap<String, Integer> counts) {
        ScheduledExecutorService counters = new ScheduledThreadPoolExecutor(numCounters);
        for (int i = 0; i < numCounters; i++) {
            counters.submit(new CounterRunnable(queue, counts));
        }
        return counters;
    }

    private boolean shutdownReaders(ScheduledExecutorService readers) throws InterruptedException {
        readers.shutdown();
        return readers.awaitTermination(24, TimeUnit.HOURS);
    }

    private boolean shutdownCounters(ScheduledExecutorService counters) throws InterruptedException {
        counters.shutdownNow();
        return counters.awaitTermination(1, TimeUnit.SECONDS);
    }

    private void waitForEmpty(BlockingQueue<String> queue) {
        while (!queue.isEmpty())
            ;
    }

    private final class CounterRunnable implements Runnable {

        private final BlockingQueue<String> queue;
        private final ConcurrentMap<String, Integer> counts;

        public CounterRunnable(BlockingQueue<String> queue, ConcurrentMap<String, Integer> counts) {
            this.queue = queue;
            this.counts = counts;
        }

        @Override
        public void run() {
            boolean finished = false;
            while (!finished) {
                try {
                    addCounts(counts, countWords(queue.take()));
                } catch (InterruptedException e) {
                    finished = true;
                }
            }
        }
    }

    private final class FileReaderRunnable implements Runnable {

        private final File dir;
        private final BlockingQueue<String> queue;

        public FileReaderRunnable(File dir, BlockingQueue<String> queue) {
            this.dir = dir;
            this.queue = queue;
        }

        @Override
        public void run() {
            FileReaderVisitor visitor = new FileReaderVisitor(queue);
            try {
                Files.walkFileTree(Paths.get(dir.getPath()),
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor);
            } catch (IOException e) {
            }
        }
    }

    private final class FileReaderVisitor implements FileVisitor<Path> {

        private final BlockingQueue<String> queue;

        public FileReaderVisitor(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            try {
                queue.put(FileUtils.readFileToString(file.toFile()));
            } catch (InterruptedException e) {
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

    }

    static void addCount(Map<String, Integer> counts, String word, int count) {
        Integer cc = counts.get(word);
        if (cc != null) {
            count += cc;
        }
        counts.put(word, count);
    }

    static void addCounts(Map<String, Integer> counts, Map<String, Integer> countsx) {
        for (Entry<String, Integer> e : countsx.entrySet()) {
            addCount(counts, e.getKey(), e.getValue());
        }
    }

}
