package com.stoyanr.wordcounter;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

final class FindTopAnalysisFactory implements AnalysisFactory<SortedMap<Integer, Set<String>>> {

    private final Set<Entry<String, Integer>> entries;
    private final int number;
    private final boolean top;

    FindTopAnalysisFactory(Set<Entry<String, Integer>> entries, int number, boolean top) {
        if (number < 0 || number > entries.size())
            throw new IllegalArgumentException();
        this.entries = entries;
        this.number = (number != 0) ? number : entries.size();
        this.top = top;
    }

    @Override
    public Analyzer<SortedMap<Integer, Set<String>>> getAnalyzer() {
        return new Analyzer<SortedMap<Integer, Set<String>>>() {
            @Override
            public SortedMap<Integer, Set<String>> analyze(int lo, int hi) {
                return findTop(lo, hi);
            }
        };
    }

    @Override
    public Merger<SortedMap<Integer, Set<String>>> getMerger() {
        return new Merger<SortedMap<Integer, Set<String>>>() {
            @Override
            public SortedMap<Integer, Set<String>> merge(SortedMap<Integer, Set<String>> r1,
                SortedMap<Integer, Set<String>> r2) {
                add(r1, r2);
                return r1;
            }
        };
    }

    private SortedMap<Integer, Set<String>> findTop(int lo, int hi) {
        // @formatter:off
        SortedMap<Integer, Set<String>> result = (top) ? 
            new TreeMap<Integer, Set<String>>(new ReverseComparator()) : 
            new TreeMap<Integer, Set<String>>();
        // @formatter:on
        Iterator<Entry<String, Integer>> it = entries.iterator();
        advance(it, lo);
        for (int i = lo; i < hi; i++) {
            Entry<String, Integer> e = it.next();
            if (result.size() < number || shouldInclude(result, e)) {
                addWord(result, e.getValue(), e.getKey());
            }
        }
        return result;
    }

    private boolean shouldInclude(SortedMap<Integer, Set<String>> m, Entry<String, Integer> e) {
        return (top) ? (e.getValue() >= m.lastKey()) : (e.getValue() <= m.lastKey());
    }

    private void add(SortedMap<Integer, Set<String>> m1, SortedMap<Integer, Set<String>> m2) {
        for (Entry<Integer, Set<String>> e : m2.entrySet()) {
            addWords(m1, e.getKey(), e.getValue());
        }
    }

    private void addWord(SortedMap<Integer, Set<String>> m, int count, String word) {
        if (m.containsKey(count)) {
            m.get(count).add(word);
        } else {
            putWords(m, count, getSet(word));
        }
    }

    private void addWords(SortedMap<Integer, Set<String>> m, int count, Set<String> words) {
        if (m.containsKey(count)) {
            m.get(count).addAll(words);
        } else {
            putWords(m, count, words);
        }
    }

    private void putWords(SortedMap<Integer, Set<String>> m, int count, Set<String> words) {
        m.put(count, words);
        if (m.size() > number) {
            m.remove(m.lastKey());
        }
    }

    private static void advance(Iterator<Entry<String, Integer>> it, int lo) {
        for (int i = 0; i < lo; i++)
            it.next();
    }

    private static Set<String> getSet(String word) {
        Set<String> set = new HashSet<>();
        set.add(word);
        return set;
    }

    static final class ReverseComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer o1, Integer o2) {
            return o2 - o1;
        }
    }
}
