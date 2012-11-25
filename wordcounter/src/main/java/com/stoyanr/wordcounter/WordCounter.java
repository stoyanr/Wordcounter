package com.stoyanr.wordcounter;

import static com.stoyanr.util.Logger.debug;
import static com.stoyanr.util.Logger.isDebug;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

class WordCounter {

    public static final String DEFAULT_DELIMITERS = " \t\n\r\f;,.:?!/\\'\"()[]{}<>+-*=~@#$%^&|`";

    private static final int PAR = Runtime.getRuntime().availableProcessors();
    private static final int MAX_ST_SIZE = 1024 * 1024;

    private static final EnumSet<FileVisitOption> OPTIONS = EnumSet
        .of(FileVisitOption.FOLLOW_LINKS);

    private final String delimiters;
    private final Set<Character> ds;

    public WordCounter() {
        this(DEFAULT_DELIMITERS);
    }

    public WordCounter(String delimiters) {
        this.delimiters = delimiters;
        ds = createDelimiterSet(delimiters);
    }

    public HashSet<Character> createDelimiterSet(String delimiters) {
        HashSet<Character> set = new HashSet<>();
        for (int i = 0; i < delimiters.length(); i++) {
            set.add(delimiters.charAt(i));
        }
        return set;
    }

    public Map<String, Integer> countWords(String text) {
        if (text == null)
            throw new IllegalArgumentException();
        return countWords(new StringTokenizer(text, delimiters));
    }

    private Map<String, Integer> countWords(StringTokenizer t) {
        assert (t != null);
        Map<String, Integer> result = new HashMap<>();
        while (t.hasMoreTokens()) {
            String word = t.nextToken();
            add(result, word, 1);
        }
        return result;
    }

    public Map<String, Integer> countWords(File file) throws IOException {
        return countWords(file, false);
    }

    public Map<String, Integer> countWords(File file, boolean parallel) throws IOException {
        if (file == null || !file.exists())
            throw new IllegalArgumentException();
        return (parallel) ? countWordsParallel(file) : countWordsSequential(file);
    }

    private Map<String, Integer> countWordsSequential(File file) throws IOException {
        Map<String, Integer> result;
        if (file.isDirectory()) {
            result = new HashMap<>();
            Files.walkFileTree(Paths.get(file.getPath()), OPTIONS, Integer.MAX_VALUE,
                new WordCounterVisitor(result));
        } else {
            result = countWords(FileUtils.readFileToString(file));
        }
        return result;
    }

    private final class WordCounterVisitor implements FileVisitor<Path> {

        private final Map<String, Integer> counts;

        public WordCounterVisitor(Map<String, Integer> counts) {
            this.counts = counts;
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
            add(counts, countWords(FileUtils.readFileToString(file.toFile())));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }

    private Map<String, Integer> countWordsParallel(File file) {
        ConcurrentMap<String, Integer> result = new ConcurrentHashMap<>(16, 0.75f, PAR);
        try {
            BlockingQueue<String> queue = new LinkedBlockingQueue<>(PAR);
            ScheduledExecutorService readers = createReaders(file, queue);
            ScheduledExecutorService counters = createCounters(queue, result);
            boolean finished = shutdownReaders(readers);
            assert (finished);
            waitForEmpty(queue);
            boolean terminated = shutdownCounters(counters);
            assert (terminated);
        } catch (InterruptedException e) {
        }
        return result;
    }

    private ScheduledExecutorService createReaders(File dir, BlockingQueue<String> queue) {
        ScheduledExecutorService readers = new ScheduledThreadPoolExecutor(1);
        readers.submit(new FileReaderRunnable(dir, queue));
        return readers;
    }

    private ScheduledExecutorService createCounters(BlockingQueue<String> queue,
        ConcurrentMap<String, Integer> counts) {
        ScheduledExecutorService counters = new ScheduledThreadPoolExecutor(PAR);
        for (int i = 0; i < PAR; i++) {
            counters.submit(new CounterRunnable(i, queue, counts));
        }
        return counters;
    }

    private boolean shutdownReaders(ScheduledExecutorService readers) throws InterruptedException {
        readers.shutdown();
        return readers.awaitTermination(24, TimeUnit.HOURS);
    }

    private boolean shutdownCounters(ScheduledExecutorService counters) throws InterruptedException {
        counters.shutdownNow();
        return counters.awaitTermination(10, TimeUnit.SECONDS);
    }

    private void waitForEmpty(BlockingQueue<String> queue) {
        while (!queue.isEmpty())
            ;
    }

    private final class CounterRunnable implements Runnable {

        private final int id;
        private final BlockingQueue<String> queue;
        private final ConcurrentMap<String, Integer> counts;
        private volatile String threadName;

        CounterRunnable(int id, BlockingQueue<String> queue, ConcurrentMap<String, Integer> counts) {
            this.id = id;
            this.queue = queue;
            this.counts = counts;
        }

        @Override
        public void run() {
            threadName = Thread.currentThread().getName();
            boolean finished = false;
            while (!finished) {
                try {
                    long t0 = logQueueEmpty();
                    String text = queue.take();
                    logWaitTime(t0);
                    add(counts, countWords(text));
                    logJobDone(text);
                } catch (InterruptedException e) {
                    finished = true;
                }
            }
        }

        private long logQueueEmpty() {
            long t0 = 0;
            if (isDebug() && queue.isEmpty()) {
                debug("[Counter %d (%s)] Queue empty, waiting ...", id, threadName);
                t0 = System.nanoTime();
            }
            return t0;
        }

        private void logWaitTime(long t0) {
            if (isDebug() && t0 != 0) {
                long t1 = System.nanoTime();
                debug("[Counter %d (%s)] Waited for %.2f us", id, threadName,
                    ((double) (t1 - t0)) / 1000);
            }
        }

        private void logJobDone(String text) {
            if (isDebug()) {
                debug("[Counter %d (%s)] Processed text '%s'", id, threadName, trim(text));
            }
        }
    }

    private final class FileReaderRunnable implements Runnable {

        private final File file;
        private final BlockingQueue<String> queue;
        private volatile String threadName;

        FileReaderRunnable(File file, BlockingQueue<String> queue) {
            assert (file != null && file.exists());
            this.file = file;
            this.queue = queue;
        }

        @Override
        public void run() {
            threadName = Thread.currentThread().getName();
            try {
                if (file.isDirectory()) {
                    Files.walkFileTree(Paths.get(file.getPath()), OPTIONS, Integer.MAX_VALUE,
                        new FileReaderVisitor());
                } else {
                    readFile(Paths.get(file.getPath()));
                }
            } catch (IOException e) {
            }
        }

        private void readFile(Path file) throws IOException {
            try {
                if (Files.size(file) <= MAX_ST_SIZE) {
                    String text = FileUtils.readFileToString(file.toFile());
                    processText(text, file);
                } else {
                    readFileToStringAsync(file, new Processor() {
                        @Override
                        public void process(String text, Path file) throws InterruptedException {
                            processText(text, file);
                        }
                    });
                }
            } catch (InterruptedException e) {
            }
        }

        private void processText(String text, Path file) throws InterruptedException {
            long t0 = logQueueFull();
            queue.put(text);
            logWaitTime(t0);
            logJobDone(file, text);
        }

        private final class FileReaderVisitor implements FileVisitor<Path> {

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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
                readFile(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        }

        private long logQueueFull() {
            long t0 = 0;
            if (isDebug() && queue.remainingCapacity() == 0) {
                debug("[Reader (%s)] Queue full, waiting ...", threadName);
                t0 = System.nanoTime();
            }
            return t0;
        }

        private void logWaitTime(long t0) {
            if (isDebug() && t0 != 0) {
                long t1 = System.nanoTime();
                debug("[Reader (%s)] Waited for %.2f us", threadName, ((double) (t1 - t0)) / 1000);
            }
        }

        private void logJobDone(Path file, String text) {
            if (isDebug()) {
                debug("[Reader (%s)] Put text '%s' (%s)", threadName, trim(text), file.toString());
            }
        }
    }

    private interface Processor {
        void process(String text, Path file) throws InterruptedException;
    }

    private void readFileToStringAsync(Path file, Processor processor) throws InterruptedException,
        IOException {
        try (AsynchronousFileChannel ac = AsynchronousFileChannel.open(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(MAX_ST_SIZE);
            String rem = "";
            int pos = 0, read = 0;
            do {
                read = read(buffer, ac, pos);
                pos += read;
                String text = Charset.defaultCharset().decode(buffer).toString();
                int ei = getEndIndex(text);
                processor.process(rem + text.substring(0, ei), file);
                rem = text.substring(ei);
            } while (read == buffer.capacity());
        } catch (IOException | InterruptedException ex) {
            throw ex;
        } catch (ExecutionException ex) {
        }
    }

    private int read(ByteBuffer buffer, AsynchronousFileChannel ac, int pos)
        throws InterruptedException, ExecutionException {
        buffer.rewind();
        Future<Integer> future = ac.read(buffer, pos);
        while (!future.isDone()) {
            Thread.yield();
        }
        buffer.flip();
        return future.get();
    }

    private int getEndIndex(String text) {
        int ei = text.length();
        while (ei > 0 && !ds.contains(text.charAt(ei - 1))) {
            ei--;
        }
        return ei;
    }

    private static void add(Map<String, Integer> m, String word, int count) {
        Integer cc = m.get(word);
        if (cc != null) {
            count += cc;
        }
        m.put(word, count);
    }

    private static void add(ConcurrentMap<String, Integer> m, String word, int count) {
        boolean put = false;
        do {
            Integer cc = m.get(word);
            if (cc != null) {
                put = m.replace(word, cc, cc + count);
            } else {
                put = (m.putIfAbsent(word, count) == null);
            }
        } while (!put);
    }

    static void add(Map<String, Integer> m1, Map<String, Integer> m2) {
        for (Entry<String, Integer> e : m2.entrySet()) {
            add(m1, e.getKey(), e.getValue());
        }
    }

    static void add(ConcurrentMap<String, Integer> m1, Map<String, Integer> m2) {
        for (Entry<String, Integer> e : m2.entrySet()) {
            add(m1, e.getKey(), e.getValue());
        }
    }

    private static String trim(String text) {
        return text.substring(0, Math.min(text.length(), 20));
    }
}
