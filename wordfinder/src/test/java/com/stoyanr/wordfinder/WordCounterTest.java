package com.stoyanr.wordfinder;

import java.io.File;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author Stoyan Rachev
 */
@RunWith(Parameterized.class)
public class WordCounterTest {

    private static final String TEXT = "one two three one two one";
    private static final Map<String, Integer> WORDCOUNTS = new HashMap<>();

    static {
        WORDCOUNTS.put("one", 3);
        WORDCOUNTS.put("two", 2);
        WORDCOUNTS.put("three", 1);
    }

    @Parameters
    public static Collection<Object[]> data() {
        Object[][] data = new Object[][]{{TEXT, WORDCOUNTS}};
        return Arrays.asList(data);
    }
    private final String text;
    private final Map<String, Integer> wordcounts;

    public WordCounterTest(String text, Map<String, Integer> wordcounts) {
        this.text = text;
        this.wordcounts = wordcounts;
    }

    @Test
    public void testCountWordsString() {
        WordCounter counter = new WordCounter();
        Map<String, Integer> result = counter.countWords(text);
        assertEquals(wordcounts, result);
    }
    
    @Test
    public void testCountWordsFile() throws Exception {
        File file = new File("words.txt");
        FileUtils.writeStringToFile(file, text);
        WordCounter counter = new WordCounter();
        Map<String, Integer> result = counter.countWords(file);
        assertEquals(wordcounts, result);
    }
    
}
