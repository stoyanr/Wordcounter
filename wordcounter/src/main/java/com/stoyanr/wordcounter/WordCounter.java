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

    private interface FileProcessor {
        void process(Path file) throws IOException;
    }

    private interface TextProcessor {
        void process(String text) throws InterruptedException;
    }

    private Map<String, Integer> countWordsSequential(File file) throws IOException {
        final Map<String, Integer> result;
        if (file.isDirectory()) {
            result = new HashMap<>();
            FileProcessor fp = new FileProcessor() {
                @Override
                public void process(Path file) throws IOException {
                    add(result, countWords(FileUtils.readFileToString(file.toFile())));
                }
            };
            Files.walkFileTree(Paths.get(file.getPath()), OPTIONS, Integer.MAX_VALUE,
                new WordCounterVisitor(fp));
        } else {
            result = countWords(FileUtils.readFileToString(file));
        }
        return result;
    }

    private final class WordCounterVisitor implements FileVisitor<Path> {

        private final FileProcessor fp;

        public WordCounterVisitor(FileProcessor fp) {
            this.fp = fp;
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
            fp.process(file);
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

    private ScheduledExecutorService createReaders(final File file,
        final BlockingQueue<String> queue) {
        ScheduledExecutorService readers = new ScheduledThreadPoolExecutor(1);
        Runnable reader = new Runnable() {
            @Override
            public void run() {
                read(file, queue);
            }
        };
        readers.submit(reader);
        return readers;
    }

    private ScheduledExecutorService createCounters(final BlockingQueue<String> queue,
        final ConcurrentMap<String, Integer> counts) {
        ScheduledExecutorService counters = new ScheduledThreadPoolExecutor(PAR);
        for (int i = 0; i < PAR; i++) {
            Runnable counter = new Runnable() {
                @Override
                public void run() {
                    count(queue, counts);
                }
            };
            counters.submit(counter);
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

    private void read(File file, BlockingQueue<String> queue) {
        try {
            if (file.isDirectory()) {
                readDirectory(Paths.get(file.getPath()), queue);
            } else {
                readFile(Paths.get(file.getPath()), queue);
            }
        } catch (IOException e) {
        }
    }

    private void readDirectory(Path dir, final BlockingQueue<String> queue)
        throws IOException {
        FileProcessor fp = new FileProcessor() {
            @Override
            public void process(Path file) throws IOException {
                readFile(file, queue);
            }
        };
        Files.walkFileTree(dir, OPTIONS, Integer.MAX_VALUE, new WordCounterVisitor(fp));
    }

    private void readFile(Path file, final BlockingQueue<String> queue) throws IOException {
        try {
            if (Files.size(file) <= MAX_ST_SIZE) {
                String text = FileUtils.readFileToString(file.toFile());
                logReaderJobDone(file, text);
                produceText(queue, text);
            } else {
                readFileToStringAsync(file, new TextProcessor() {
                    @Override
                    public void process(String text) throws InterruptedException {
                        produceText(queue, text);
                    }
                });
            }
        } catch (InterruptedException e) {
        }
    }

    private void readFileToStringAsync(Path file, TextProcessor processor)
        throws InterruptedException, IOException {
        try (AsynchronousFileChannel ac = AsynchronousFileChannel.open(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(MAX_ST_SIZE);
            String rem = "";
            int pos = 0, read = 0;
            do {
                read = readBuffer(buffer, ac, pos);
                pos += read;
                String text = Charset.defaultCharset().decode(buffer).toString();
                int ei = getEndIndex(text);
                logReaderJobDone(file, text);
                processor.process(rem + text.substring(0, ei));
                rem = text.substring(ei);
            } while (read == buffer.capacity());
        } catch (IOException | InterruptedException ex) {
            throw ex;
        } catch (ExecutionException ex) {
        }
    }

    private int readBuffer(ByteBuffer buffer, AsynchronousFileChannel ac, int pos)
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

    private void count(BlockingQueue<String> queue, ConcurrentMap<String, Integer> counts) {
        boolean finished = false;
        while (!finished) {
            try {
                String text = consumeText(queue);
                add(counts, countWords(text));
                logCounterJobDone(text);
            } catch (InterruptedException e) {
                finished = true;
            }
        }
    }

    private static void produceText(BlockingQueue<String> queue, String text)
        throws InterruptedException {
        long t0 = logReaderQueueFull(queue);
        queue.put(text);
        logReaderWaitTime(t0);
    }

    private static String consumeText(BlockingQueue<String> queue)
        throws InterruptedException {
        long t0 = logCounterQueueEmpty(queue);
        String text = queue.take();
        logCounterWaitTime(t0);
        return text;
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

    private static long logReaderQueueFull(BlockingQueue<String> queue) {
        long t0 = 0;
        if (isDebug() && queue.remainingCapacity() == 0) {
            debug("[Reader (%s)] Queue full, waiting ...", getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private static void logReaderWaitTime(long t0) {
        if (isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            debug("[Reader (%s)] Waited for %.2f us", getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private static void logReaderJobDone(Path file, String text) {
        if (isDebug()) {
            debug("[Reader (%s)] Read text '%s' (%s)", getThreadName(), trim(text), file.toString());
        }
    }

    private static long logCounterQueueEmpty(BlockingQueue<String> queue) {
        long t0 = 0;
        if (isDebug() && queue.isEmpty()) {
            debug("[Counter (%s)] Queue empty, waiting ...", getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private static void logCounterWaitTime(long t0) {
        if (isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            debug("[Counter (%s)] Waited for %.2f us", getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private static void logCounterJobDone(String text) {
        if (isDebug()) {
            debug("[Counter (%s)] Processed text '%s'", getThreadName(), trim(text));
        }
    }

    private static String trim(String text) {
        return text.substring(0, Math.min(text.length(), 20));
    }

    private static String getThreadName() {
        return Thread.currentThread().getName();
    }
}
