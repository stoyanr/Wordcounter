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

public class ProducerConsumerExecutor<T1, T2> {
    
    public static final int DEFAULT_PAR_LEVEL = Runtime.getRuntime().availableProcessors();
    
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
    
    public interface Mediator<T1, T2> {
        void mediate(Taker<T1> taker, Putter<T2> putter);
    }
    
    private final Producer<T1> producer;
    private final Mediator<T1, T2> mediator;
    private final Consumer<T2> consumer;
    private final int parLevel;
    private final BlockingQueue<T1> q1;
    private final BlockingQueue<T2> q2;
    private volatile ScheduledExecutorService producers = null;
    private volatile ScheduledExecutorService mediators = null;
    private volatile ScheduledExecutorService consumers = null;
    
    public ProducerConsumerExecutor(Producer<T1> producer, Mediator<T1, T2> mediator, 
        Consumer<T2> consumer) {
        this(producer, mediator, consumer, DEFAULT_PAR_LEVEL);
    }

    public ProducerConsumerExecutor(Producer<T1> producer, Mediator<T1, T2> mediator, 
        Consumer<T2> consumer, int parLevel) {
        if (producer == null || mediator == null || consumer == null) {
            throw new NullPointerException();
        }
        this.producer = producer;
        this.mediator = mediator;
        this.consumer = consumer;
        this.parLevel = parLevel;
        this.q1 = new LinkedBlockingQueue<>(parLevel);
        this.q2 = new LinkedBlockingQueue<>(parLevel);
    }
    
    public void execute() {
        if (producers != null || mediators != null || consumers != null) {
            throw new IllegalStateException();
        }
        q1.clear();
        q2.clear();
        producers = createProducers();
        mediators = createMediators();
        consumers = createConsumers();
        try {
            shutdown(producers);
            while (!q1.isEmpty()) {
                Thread.yield();
            }
            shutdown(mediators);
            while (!q2.isEmpty()) {
                Thread.yield();
            }
            shutdown(consumers);
        } catch (InterruptedException e) {
            throw new RuntimeException(String.format("Interrupted: %s", e.getMessage()), e);
        }
        producers = null;
        mediators = null;
        consumers = null;
    }
    
    private ScheduledExecutorService createProducers() {
        ScheduledExecutorService producers = new ScheduledThreadPoolExecutor(1);
        producers.submit(() -> producer.produce(this::put1));
        return producers;
    }

    private ScheduledExecutorService createMediators() {
        ScheduledExecutorService mediators = new ScheduledThreadPoolExecutor(parLevel);
        for (int i = 0; i < parLevel; i++) {
            mediators.submit(() -> mediator.mediate(this::take1, this::put2));
        }
        return mediators;
    }

    private ScheduledExecutorService createConsumers() {
        ScheduledExecutorService consumers = new ScheduledThreadPoolExecutor(parLevel);
        for (int i = 0; i < parLevel; i++) {
            consumers.submit(() -> consumer.consume(this::take2));
        }
        return consumers;
    }

    private boolean shutdown(ScheduledExecutorService ses) throws InterruptedException {
        ses.shutdown();
        return ses.awaitTermination(24, TimeUnit.HOURS);
    }

    private void put1(T1 t) throws InterruptedException {
        logDone("Producer", t);
        long t0 = logQueueFull("Producer", q1);
        q1.put(t);
        logWaitTime("Producer", t0);
    }

    private T1 take1() throws InterruptedException {
        long t0 = logQueueEmpty("Mediator", q1);
        T1 t;
        do {
            t = q1.poll(1, TimeUnit.MILLISECONDS);
        } while (t == null && !mediators.isShutdown());
        logWaitTime("Mediator", t0);
        logDone("Mediator", t);
        return t;
    }
    
    private void put2(T2 t) throws InterruptedException {
        logDone("Mediator", t);
        long t0 = logQueueFull("Mediator", q2);
        q2.put(t);
        logWaitTime("Mediator", t0);
    }

    private T2 take2() throws InterruptedException {
        long t0 = logQueueEmpty("Consumer", q2);
        T2 t;
        do {
            t = q2.poll(1, TimeUnit.MILLISECONDS);
        } while (t == null && !consumers.isShutdown());
        logWaitTime("Consumer", t0);
        logDone("Consumer", t);
        return t;
    }

    private <T> long logQueueFull(String name, BlockingQueue<T> q) {
        long t0 = 0;
        if (Logger.isDebug() && q.remainingCapacity() == 0) {
            Logger.debug("[%s (%s)] Queue full, waiting ...", name, getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private void logWaitTime(String name, long t0) {
        if (Logger.isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            Logger.debug("[%s (%s)] Waited for %.2f us", name, getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private <T> void logDone(String name, T t) {
        if (Logger.isDebug() && t!= null) {
            Logger.debug("[%s (%s)] Done '%s'", name, getThreadName(), trim(t.toString()));
        }
    }

    private <T> long logQueueEmpty(String name, BlockingQueue<T> q) {
        long t0 = 0;
        if (Logger.isDebug() && q.isEmpty()) {
            Logger.debug("[%s (%s)] Queue empty, waiting ...", name, getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private static String trim(String text) {
        return text.substring(0, Math.min(text.length(), 20));
    }

    private static String getThreadName() {
        return Thread.currentThread().getName();
    }


}
