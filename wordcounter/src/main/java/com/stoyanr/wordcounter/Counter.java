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

import static com.stoyanr.util.Logger.debug;
import static com.stoyanr.util.Logger.isDebug;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

class Counter {
    
    private final BlockingQueue<String> queue;
    private final ConcurrentMap<String, Integer> counts;
    private final WordCounter wc;
    
    public Counter(BlockingQueue<String> queue, ConcurrentMap<String, Integer> counts,
        WordCounter wc) {
        this.queue = queue;
        this.counts = counts;
        this.wc = wc;
    }

    void count() {
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

    private String consumeText() throws InterruptedException {
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
        boolean put;
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
