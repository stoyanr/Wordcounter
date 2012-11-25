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

    public SortedMap<Integer, Set<String>> analyze(Map<String, Integer> counts, int top) {
        return analyze(counts, top, false);
    }

    public SortedMap<Integer, Set<String>> analyze(Map<String, Integer> counts, int top,
        boolean parallel) {
        if (top < 0 || top > counts.size())
            throw new IllegalArgumentException();
        Set<Entry<String, Integer>> entries = counts.entrySet();
        if (parallel) {
            return forkJoinPool.invoke(new AnalyzeTask(entries, 0, entries.size(), top));
        } else {
            return analyze(entries, 0, entries.size(), top);
        }
    }

    private SortedMap<Integer, Set<String>> analyze(Set<Entry<String, Integer>> entries, int lo,
        int hi, int top) {
        SortedMap<Integer, Set<String>> result = new TreeMap<Integer, Set<String>>(
            new ReverseComparator());
        Iterator<Entry<String, Integer>> it = entries.iterator();
        for (int i = 0; i < lo; i++)
            it.next();
        for (int i = lo; i < hi; i++) {
            Entry<String, Integer> e = it.next();
            String word = e.getKey();
            int count = e.getValue();
            if (result.size() < top || count >= result.lastKey()) {
                if (result.containsKey(count)) {
                    result.get(count).add(word);
                } else {
                    Set<String> set = new HashSet<>();
                    set.add(word);
                    result.put(count, set);
                    if (result.size() > top) {
                        result.remove(result.lastKey());
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("serial")
    private final class AnalyzeTask extends RecursiveTask<SortedMap<Integer, Set<String>>> {

        private final Set<Entry<String, Integer>> entries;
        private final int lo;
        private final int hi;
        private final int top;
        private final int threshold;
        private volatile String threadName;

        AnalyzeTask(Set<Entry<String, Integer>> entries, int lo, int hi, int top) {
            this.entries = entries;
            this.lo = lo;
            this.hi = hi;
            this.top = top;
            this.threshold = Math.max(entries.size() / PAR, MIN_THRESHOLD);
        }

        @Override
        protected SortedMap<Integer, Set<String>> compute() {
            threadName = Thread.currentThread().getName();
            logStarting();
            SortedMap<Integer, Set<String>> result;
            if (hi - lo <= threshold) {
                result = analyze(entries, lo, hi, top);
            } else {
                int mid = (lo + hi) >>> 1;
                AnalyzeTask t1 = new AnalyzeTask(entries, lo, mid, top);
                t1.fork();
                AnalyzeTask t2 = new AnalyzeTask(entries, mid, hi, top);
                SortedMap<Integer, Set<String>> m2 = t2.compute();
                SortedMap<Integer, Set<String>> m1 = t1.join();
                add(m1, m2, top);
                result = m1;
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

    private static void add(SortedMap<Integer, Set<String>> m1, SortedMap<Integer, Set<String>> m2,
        int top) {
        for (Entry<Integer, Set<String>> e : m2.entrySet()) {
            int count = e.getKey();
            Set<String> words = e.getValue();
            if (m1.containsKey(count)) {
                m1.get(count).addAll(words);
            } else {
                m1.put(count, words);
                if (m1.size() > top) {
                    m1.remove(m1.lastKey());
                }
            }
        }
    }

    static final class ReverseComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer o1, Integer o2) {
            return o2 - o1;
        }
    }
}
