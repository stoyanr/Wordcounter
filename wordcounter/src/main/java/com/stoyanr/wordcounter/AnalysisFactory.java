package com.stoyanr.wordcounter;

interface AnalysisFactory<T> {
    Analyzer<T> getAnalyzer();

    Merger<T> getMerger();
}
