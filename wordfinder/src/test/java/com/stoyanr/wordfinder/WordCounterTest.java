package com.stoyanr.wordfinder;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WordCounterTest {

    private static final String TEXT1 = "one two three one two one";
    private static final String TEXT2 = "five six\tseven123#five";
    private static final String TEXT3 = "eight; nine\t?!ten10<eleven...eight";
    private static final Map<String, Integer> COUNTS1 = new HashMap<>();
    private static final Map<String, Integer> COUNTS2 = new HashMap<>();
    private static final Map<String, Integer> COUNTS3 = new HashMap<>();

    private static final String DELIMS = WordCounter.DEFAULT_DELIMITERS
        + ";,.:?!/\\'\"()[]{}<>+-*=~@#$%^&|`";

    private static final String DIR = "words";
    private static final String FILE = "words.txt";

    static {
        // @formatter:off
        COUNTS1.put("one", 3); COUNTS1.put("two", 2); COUNTS1.put("three", 1);
        COUNTS2.put("five", 2); COUNTS2.put("six", 1); COUNTS2.put("seven123", 1);
        COUNTS3.put("eight", 2); COUNTS3.put("nine", 1); COUNTS3.put("ten10", 1); COUNTS3.put("eleven", 1);
        // @formatter:on
    }

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { asList(TEXT1), asList(COUNTS1) }, 
            { asList(TEXT2), asList(COUNTS2) }, 
            { asList(TEXT1, TEXT2), asList(COUNTS1, COUNTS2) }, 
            { asList(TEXT3), asList(COUNTS3) }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final List<String> texts;
    private final List<Map<String, Integer>> countsList;

    private WordCounter counter;

    public WordCounterTest(List<String> texts, List<Map<String, Integer>> countsList) {
        this.texts = texts;
        this.countsList = countsList;
    }

    @Before
    public void setUp() {
        counter = new WordCounter(DELIMS, 4);
    }

    @Test
    public void testCountWordsString() {
        Map<String, Integer> result = counter.countWords(text());
        assertEquals(combine(), result);
    }

    @Test
    public void testCountWordsFile() throws Exception {
        Map<String, Integer> result = counter.countWords(file());
        assertEquals(combine(), result);
    }

    @Test
    public void testCountWordsTree() throws Exception {
        Map<String, Integer> result = counter.countWords(tree());
        Map<String, Integer> expected = combine();
        TestUtils.assertEqualMaps(expected, result);
    }

    private String text() {
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            sb.append(text + "\n");
        }
        return sb.toString();
    }

    private File file() throws IOException {
        File file = new File(FILE);
        FileUtils.writeStringToFile(file, text());
        return file;
    }

    private File tree() throws IOException {
        File dir = new File(DIR);
        TestUtils.deleteDir(dir);
        dir.mkdirs();
        int count = 0;
        for (String text : texts) {
            File dirx = new File(DIR + "/" + count);
            dirx.mkdirs();
            FileUtils.writeStringToFile(new File(DIR + "/" + count + "/" + FILE), text);
            count++;
        }
        return dir;
    }

    private Map<String, Integer> combine() {
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Integer> counts : countsList) {
            WordCounter.addCounts(result, counts);
        }
        return result;
    }
}
