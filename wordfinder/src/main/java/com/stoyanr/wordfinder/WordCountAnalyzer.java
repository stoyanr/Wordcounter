package com.stoyanr.wordfinder;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class WordCountAnalyzer {

    public SortedMap<Integer, Set<String>> analyze(Map<String, Integer> counts, int top) {
        assert (top > 0 && top <= counts.size());
        SortedMap<Integer, Set<String>> result = new TreeMap<Integer, Set<String>>(new ReverseComparator());
        for (Entry<String, Integer> e : counts.entrySet()) {
            String word = e.getKey();
            int count = e.getValue();
            if (result.size() < top || count >= result.lastKey()) {
                if (result.containsKey(count)) {
                    result.get(count).add(word);
                } else {
                    Set<String> set = new HashSet<>();
                    set.add(word);
                    result.put(count, set);
                    if (result.size() > top) {
                        result.remove(result.lastKey());
                    }
                }
            }
        }
        return result;
    }

    public static final class ReverseComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer o1, Integer o2) {
            return o2 - o1;
        }
    }
}
