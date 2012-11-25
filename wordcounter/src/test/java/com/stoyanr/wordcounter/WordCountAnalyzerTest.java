package com.stoyanr.wordcounter;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.stoyanr.wordcounter.WordCountAnalyzer;

@RunWith(Parameterized.class)
public class WordCountAnalyzerTest {

    private static final Map<String, Integer> COUNTS1 = new HashMap<>();
    private static final Map<String, Integer> COUNTS2 = new HashMap<>();
    private static final Map<String, Integer> COUNTS3 = new HashMap<>();
    private static final SortedMap<Integer, Set<String>> SORTED1 = new TreeMap<>(comparator());
    private static final SortedMap<Integer, Set<String>> SORTED2 = new TreeMap<>(comparator());
    private static final SortedMap<Integer, Set<String>> SORTED3 = new TreeMap<>(comparator());

    static {
        // @formatter:off
        COUNTS1.put("one", 3); COUNTS1.put("two", 2); COUNTS1.put("three", 1);
        COUNTS2.put("five", 2); COUNTS2.put("six", 1); COUNTS2.put("seven123", 1);
        COUNTS3.put("eight", 2); COUNTS3.put("nine", 1); COUNTS3.put("ten10", 1); COUNTS3.put("eleven", 1);
        
        SORTED1.put(3, asSet("one")); SORTED1.put(2, asSet("two")); SORTED1.put(1, asSet("three")); 
        SORTED2.put(2, asSet("five")); SORTED2.put(1, asSet("six", "seven123")); 
        SORTED3.put(2, asSet("eight")); SORTED3.put(1, asSet("nine", "ten10", "eleven")); 
        // @formatter:on
    }

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { COUNTS1, SORTED1, 2 }, 
            { COUNTS2, SORTED2, 1 }, 
            { COUNTS3, SORTED3, 2 }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final Map<String, Integer> counts;
    private final SortedMap<Integer, Set<String>> sorted;
    private final int top;

    private WordCountAnalyzer analyzer;

    public WordCountAnalyzerTest(Map<String, Integer> counts,
        SortedMap<Integer, Set<String>> sorted, int top) {
        this.counts = counts;
        this.sorted = sorted;
        this.top = top;
    }

    @Before
    public void setUp() throws Exception {
        analyzer = new WordCountAnalyzer();
    }

    @Test
    public void testAnalyze() {
        assertEquals(TestUtils.getHead(sorted, top), analyzer.findTop(counts, top, true));
    }

    @Test
    public void testAnalyzeParallel() {
        assertEquals(TestUtils.getHead(sorted, top), analyzer.findTop(counts, top, true, true));
    }

    private static Set<String> asSet(String... strings) {
        return new HashSet<>(asList(strings));
    }

    private static Comparator<Integer> comparator() {
        return new FindTopAnalysisFactory.ReverseComparator();
    }

}
