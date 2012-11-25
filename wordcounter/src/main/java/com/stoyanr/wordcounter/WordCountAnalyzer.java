package com.stoyanr.wordcounter;

import static com.stoyanr.util.Logger.debug;
import static com.stoyanr.util.Logger.isDebug;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
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
        Set<Entry<String, Integer>> entries = counts.entrySet();
        AnalysisFactory<SortedMap<Integer, Set<String>>> op = new FindTopAnalysisFactory(entries,
            number, top);
        return execute(op, entries.size(), parallel);
    }

    private <T> T execute(AnalysisFactory<T> op, int size, boolean parallel) {
        if (parallel) {
            return forkJoinPool.invoke(new AnalyzerTask<T>(0, size, size, op.getAnalyzer(),
                op.getMerger()));
        } else {
            return op.getAnalyzer().analyze(0, size);
        }
    }

    @SuppressWarnings("serial")
    private final class AnalyzerTask<T> extends RecursiveTask<T> {

        private final int lo;
        private final int hi;
        private final int size;
        private final Analyzer<T> analyzer;
        private final Merger<T> merger;

        AnalyzerTask(int lo, int hi, int size, Analyzer<T> analyzer, Merger<T> merger) {
            this.lo = lo;
            this.hi = hi;
            this.size = size;
            this.analyzer = analyzer;
            this.merger = merger;
        }

        @Override
        protected T compute() {
            logStarting();
            T result;
            if (hi - lo <= Math.max(size / PAR, MIN_THRESHOLD)) {
                result = analyzer.analyze(lo, hi);
            } else {
                int mid = (lo + hi) >>> 1;
                AnalyzerTask<T> t1 = new AnalyzerTask<T>(lo, mid, size, analyzer, merger);
                t1.fork();
                AnalyzerTask<T> t2 = new AnalyzerTask<T>(mid, hi, size, analyzer, merger);
                T result2 = t2.compute();
                T result1 = t1.join();
                result = merger.merge(result1, result2);
            }
            logFinished();
            return result;
        }

        private void logStarting() {
            if (isDebug()) {
                debug("[Analyzer %d - %d (%s)] Starting ...", lo, hi, getThreadName());
            }
        }

        private void logFinished() {
            if (isDebug()) {
                debug("[Analyzer %d - %d (%s)] Finished", lo, hi, getThreadName());
            }
        }
    }

    private static String getThreadName() {
        return Thread.currentThread().getName();
    }

}
