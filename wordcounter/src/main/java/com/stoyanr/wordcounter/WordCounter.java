/*
 * $Id: $
 *
 * Copyright 2012 Stoyan Rachev (stoyanr@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stoyanr.wordcounter;

import com.stoyanr.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WordCounter {

    public static final String DEFAULT_DELIMITERS = " \t\n\r\f;,.:?!/\\'\"()[]{}<>+-*=~@#$%^&|`";

    private static final int PAR = Runtime.getRuntime().availableProcessors();

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

    private HashSet<Character> createDelimiterSet(String delimiters) {
        HashSet<Character> set = new HashSet<>();
        for (int i = 0; i < delimiters.length(); i++) {
            set.add(delimiters.charAt(i));
        }
        return set;
    }

    public Map<String, Integer> countWords(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text is null");
        }
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
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File is null or doesn't exist.");
        }
        return (parallel) ? countWordsParallel(file) : countWordsSequential(file);
    }

    private Map<String, Integer> countWordsSequential(File file) throws IOException {
        final Map<String, Integer> result;
        if (file.isDirectory()) {
            result = new HashMap<>();
            Files.walkFileTree(Paths.get(file.getPath()), OPTIONS, Integer.MAX_VALUE,
                new WordCounterFileVisitor((filex) -> 
                    add(result, countWords(FileUtils.readFileToString(filex)))));
        } else {
            result = countWords(FileUtils.readFileToString(Paths.get(file.getPath())));
        }
        return result;
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
            throw new WordCounterException(String.format("Interrupted while counting: %s", 
                e.getMessage()), e);
        }
        return result;
    }

    private ScheduledExecutorService createReaders(final File file,
        final BlockingQueue<String> queue) {
        ScheduledExecutorService readers = new ScheduledThreadPoolExecutor(1);
        readers.submit(new Reader(file, queue, this)::read);
        return readers;
    }

    private ScheduledExecutorService createCounters(final BlockingQueue<String> queue,
        final ConcurrentMap<String, Integer> counts) {
        ScheduledExecutorService counters = new ScheduledThreadPoolExecutor(PAR);
        for (int i = 0; i < PAR; i++) {
            counters.submit(new Counter(queue, counts, this)::count);
        }
        return counters;
    }

    private boolean shutdownReaders(ScheduledExecutorService readers) throws InterruptedException {
        readers.shutdown();
        return readers.awaitTermination(24, TimeUnit.HOURS);
    }

    private boolean shutdownCounters(ScheduledExecutorService counters) throws InterruptedException {
        counters.shutdownNow();
        return counters.awaitTermination(2, TimeUnit.SECONDS);
    }

    @SuppressWarnings("empty-statement")
    private void waitForEmpty(BlockingQueue<String> queue) {
        while (!queue.isEmpty())
            ;
    }
    
    int getEndIndex(String text) {
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

    static void add(Map<String, Integer> m1, Map<String, Integer> m2) {
        for (Entry<String, Integer> e : m2.entrySet()) {
            add(m1, e.getKey(), e.getValue());
        }
    }
}
