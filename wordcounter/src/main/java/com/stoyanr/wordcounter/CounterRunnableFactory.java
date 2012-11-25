package com.stoyanr.wordcounter;

import static com.stoyanr.util.Logger.debug;
import static com.stoyanr.util.Logger.isDebug;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

class CounterRunnableFactory implements RunnableFactory {
    
    private final BlockingQueue<String> queue;
    private final ConcurrentMap<String, Integer> counts;
    private final WordCounter wc;
    
    public CounterRunnableFactory(BlockingQueue<String> queue, ConcurrentMap<String, Integer> counts,
        WordCounter wc) {
        this.queue = queue;
        this.counts = counts;
        this.wc = wc;
    }

    @Override
    public Runnable getRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                count();
            }
        };
    }

    private void count() {
        boolean finished = false;
        while (!finished) {
            try {
                String text = consumeText();
                add(counts, wc.countWords(text));
                logCounterJobDone(text);
            } catch (InterruptedException e) {
                finished = true;
            }
        }
    }

    private String consumeText()
        throws InterruptedException {
        long t0 = logCounterQueueEmpty();
        String text = queue.take();
        logCounterWaitTime(t0);
        return text;
    }

    private long logCounterQueueEmpty() {
        long t0 = 0;
        if (isDebug() && queue.isEmpty()) {
            debug("[Counter (%s)] Queue empty, waiting ...", getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private void logCounterWaitTime(long t0) {
        if (isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            debug("[Counter (%s)] Waited for %.2f us", getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private void logCounterJobDone(String text) {
        if (isDebug()) {
            debug("[Counter (%s)] Processed text '%s'", getThreadName(), trim(text));
        }
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

    private static void add(ConcurrentMap<String, Integer> m1, Map<String, Integer> m2) {
        for (Entry<String, Integer> e : m2.entrySet()) {
            add(m1, e.getKey(), e.getValue());
        }
    }

    private static String trim(String text) {
        return text.substring(0, Math.min(text.length(), 20));
    }

    private static String getThreadName() {
        return Thread.currentThread().getName();
    }
}
