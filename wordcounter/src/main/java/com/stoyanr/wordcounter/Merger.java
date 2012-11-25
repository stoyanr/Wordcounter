package com.stoyanr.wordcounter;

interface Merger<T> {
    T merge(T result1, T result2);
}