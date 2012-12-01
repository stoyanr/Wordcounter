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
package com.stoyanr.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ProducerConsumerComputer<T> {
    
    private static final int PAR_LEVEL = Runtime.getRuntime().availableProcessors();
    
    public interface Putter<T> {
        void put(T t) throws InterruptedException;
    }

    public interface Taker<T> {
        T take() throws InterruptedException;
    }

    public interface Producer<T> {
        void produce(Putter<T> putter);
    }
    
    public interface Consumer<T> {
        void consume(Taker<T> taker);
    }
    
    private final Producer<T> producer;
    private final Consumer<T> consumer;
    private final BlockingQueue<T> queue = new LinkedBlockingQueue<>(PAR_LEVEL);
    
    public ProducerConsumerComputer(Producer<T> producer, Consumer<T> consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }
    
    public void compute() {
        try {
            queue.clear();
            ScheduledExecutorService producers = createProducers();
            ScheduledExecutorService consumers = createConsumers();
            boolean finished = shutdownProducers(producers);
            assert (finished);
            waitForEmpty();
            boolean terminated = shutdownConsumers(consumers);
            assert (terminated);
        } catch (InterruptedException e) {
            throw new RuntimeException(String.format("Interrupted: %s", e.getMessage()), e);
        }
    }

    private ScheduledExecutorService createProducers() {
        ScheduledExecutorService readers = new ScheduledThreadPoolExecutor(1);
        readers.submit(() -> producer.produce(this::put));
        return readers;
    }

    private ScheduledExecutorService createConsumers() {
        ScheduledExecutorService counters = new ScheduledThreadPoolExecutor(PAR_LEVEL);
        for (int i = 0; i < PAR_LEVEL; i++) {
            counters.submit(() -> consumer.consume(this::take));
        }
        return counters;
    }

    private boolean shutdownProducers(ScheduledExecutorService readers) throws InterruptedException {
        readers.shutdown();
        return readers.awaitTermination(24, TimeUnit.HOURS);
    }

    private boolean shutdownConsumers(ScheduledExecutorService counters) throws InterruptedException {
        counters.shutdownNow();
        return counters.awaitTermination(2, TimeUnit.SECONDS);
    }

    private void waitForEmpty() {
        while (!queue.isEmpty()) {
            Thread.yield();
        }
    }
    
    private void put(T t) throws InterruptedException {
        logProducerJobDone(t);
        long t0 = logProducerQueueFull();
        queue.put(t);
        logProducerWaitTime(t0);
    }

    private T take() throws InterruptedException {
        long t0 = logConsumerQueueEmpty();
        T t = queue.take();
        logConsumerWaitTime(t0);
        logConsumerJobDone(t);
        return t;
    }

    private long logProducerQueueFull() {
        long t0 = 0;
        if (Logger.isDebug() && queue.remainingCapacity() == 0) {
            Logger.debug("[Producer (%s)] Queue full, waiting ...", getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private void logProducerWaitTime(long t0) {
        if (Logger.isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            Logger.debug("[Producer (%s)] Waited for %.2f us", getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private void logProducerJobDone(T t) {
        if (Logger.isDebug()) {
            Logger.debug("[Producer (%s)] Produced '%s'", getThreadName(), trim(t.toString()));
        }
    }

    private long logConsumerQueueEmpty() {
        long t0 = 0;
        if (Logger.isDebug() && queue.isEmpty()) {
            Logger.debug("[Consumer (%s)] Queue empty, waiting ...", getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private void logConsumerWaitTime(long t0) {
        if (Logger.isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            Logger.debug("[Consumer (%s)] Waited for %.2f us", getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private void logConsumerJobDone(T t) {
        if (Logger.isDebug()) {
            Logger.debug("[Consumer (%s)] Consumed '%s'", getThreadName(), trim(t.toString()));
        }
    }

    private static String trim(String text) {
        return text.substring(0, Math.min(text.length(), 20));
    }

    private static String getThreadName() {
        return Thread.currentThread().getName();
    }


}
