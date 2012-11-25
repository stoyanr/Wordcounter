package com.stoyanr.wordcounter;

interface TextProcessor<T> {
    T process(String text, T state) throws InterruptedException;
}