package com.stoyanr.wordcounter;

import static com.stoyanr.util.Logger.debug;
import static com.stoyanr.util.Logger.isDebug;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class WordCountAnalyzer {

    public static final int PAR = Runtime.getRuntime().availableProcessors();
    public static final int MIN_THRESHOLD = 32 * 1024;

    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

    public SortedMap<Integer, Set<String>> findTop(Map<String, Integer> counts, int number,
        boolean top) {
        return findTop(counts, number, top, false);
    }

    public SortedMap<Integer, Set<String>> findTop(Map<String, Integer> counts, final int number,
        final boolean top, boolean parallel) {
        if (number < 0 || number > counts.size())
            throw new IllegalArgumentException();
        final int numberx = (number != 0) ? number : counts.size();
        Set<Entry<String, Integer>> entries = counts.entrySet();
        if (parallel) {
            Analyzer<SortedMap<Integer, Set<String>>> analyzer = new Analyzer<SortedMap<Integer, Set<String>>>() {
                @Override
                public SortedMap<Integer, Set<String>> analyze(Set<Entry<String, Integer>> entries,
                    int lo, int hi) {
                    return findTop(entries, lo, hi, numberx, top);
                }
            };
            Merger<SortedMap<Integer, Set<String>>> merger = new Merger<SortedMap<Integer, Set<String>>>() {
                @Override
                public SortedMap<Integer, Set<String>> merge(SortedMap<Integer, Set<String>> r1,
                    SortedMap<Integer, Set<String>> r2) {
                    add(r1, r2, numberx);
                    return r1;
                }
            };
            return forkJoinPool.invoke(new AnalyzerTask<SortedMap<Integer, Set<String>>>(entries,
                0, entries.size(), analyzer, merger));
        } else {
            return findTop(entries, 0, entries.size(), numberx, top);
        }
    }

    private interface Analyzer<T> {
        T analyze(Set<Entry<String, Integer>> entries, int lo, int hi);
    }

    private interface Merger<T> {
        T merge(T result1, T result2);
    }

    @SuppressWarnings("serial")
    private final class AnalyzerTask<T> extends RecursiveTask<T> {

        private final Set<Entry<String, Integer>> entries;
        private final int lo;
        private final int hi;
        private final Analyzer<T> analyzer;
        private final Merger<T> merger;
        private final int threshold;
        private volatile String threadName;

        AnalyzerTask(Set<Entry<String, Integer>> entries, int lo, int hi, Analyzer<T> analyzer,
            Merger<T> merger) {
            this.entries = entries;
            this.lo = lo;
            this.hi = hi;
            this.analyzer = analyzer;
            this.merger = merger;
            this.threshold = Math.max(entries.size() / PAR, MIN_THRESHOLD);
        }

        @Override
        protected T compute() {
            threadName = Thread.currentThread().getName();
            logStarting();
            T result;
            if (hi - lo <= threshold) {
                result = analyzer.analyze(entries, lo, hi);
            } else {
                int mid = (lo + hi) >>> 1;
                AnalyzerTask<T> t1 = new AnalyzerTask<T>(entries, lo, mid, analyzer, merger);
                t1.fork();
                AnalyzerTask<T> t2 = new AnalyzerTask<T>(entries, mid, hi, analyzer, merger);
                T result2 = t2.compute();
                T result1 = t1.join();
                result = merger.merge(result1, result2);
            }
            logFinished();
            return result;
        }

        private void logStarting() {
            if (isDebug()) {
                debug("[Analyzer %d - %d (%s)] Starting ...", lo, hi, threadName);
            }
        }

        private void logFinished() {
            if (isDebug()) {
                debug("[Analyzer %d - %d (%s)] Finished", lo, hi, threadName);
            }
        }
    }

    private static SortedMap<Integer, Set<String>> findTop(Set<Entry<String, Integer>> entries,
        int lo, int hi, int number, boolean top) {
        // @formatter:off
        SortedMap<Integer, Set<String>> result = (top) ? 
            new TreeMap<Integer, Set<String>>(new ReverseComparator()) : 
            new TreeMap<Integer, Set<String>>();
        // @formatter:on
        Iterator<Entry<String, Integer>> it = entries.iterator();
        advance(it, lo);
        for (int i = lo; i < hi; i++) {
            Entry<String, Integer> e = it.next();
            if (result.size() < number || shouldInclude(result, e, top)) {
                addWord(result, e.getValue(), e.getKey(), number);
            }
        }
        return result;
    }

    private static boolean shouldInclude(SortedMap<Integer, Set<String>> m,
        Entry<String, Integer> e, boolean top) {
        return (top) ? (e.getValue() >= m.lastKey()) : (e.getValue() <= m.lastKey());
    }

    private static void add(SortedMap<Integer, Set<String>> m1, SortedMap<Integer, Set<String>> m2,
        int number) {
        for (Entry<Integer, Set<String>> e : m2.entrySet()) {
            addWords(m1, e.getKey(), e.getValue(), number);
        }
    }

    private static void advance(Iterator<Entry<String, Integer>> it, int lo) {
        for (int i = 0; i < lo; i++)
            it.next();
    }

    private static void addWord(SortedMap<Integer, Set<String>> m, int count, String word,
        int number) {
        if (m.containsKey(count)) {
            m.get(count).add(word);
        } else {
            putWords(m, count, getSet(word), number);
        }
    }

    private static void addWords(SortedMap<Integer, Set<String>> m, int count, Set<String> words,
        int number) {
        if (m.containsKey(count)) {
            m.get(count).addAll(words);
        } else {
            putWords(m, count, words, number);
        }
    }

    private static Set<String> getSet(String word) {
        Set<String> set = new HashSet<>();
        set.add(word);
        return set;
    }

    private static void putWords(SortedMap<Integer, Set<String>> m, int count, Set<String> words,
        int number) {
        m.put(count, words);
        if (m.size() > number) {
            m.remove(m.lastKey());
        }
    }

    static final class ReverseComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer o1, Integer o2) {
            return o2 - o1;
        }
    }
}
