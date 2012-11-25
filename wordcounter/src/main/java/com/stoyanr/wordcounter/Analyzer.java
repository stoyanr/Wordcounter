package com.stoyanr.wordcounter;

interface Analyzer<T> {
    T analyze(int lo, int hi);
}