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

/**
 * @author Stoyan Rachev
 */
@RunWith(Parameterized.class)
public class WordCounterTest {

    private static final String TEXT1 = "one two three one two one";
    private static final String TEXT2 = "five six\tseven123\nfive";
    private static final Map<String, Integer> COUNTS1 = new HashMap<>();
    private static final Map<String, Integer> COUNTS2 = new HashMap<>();

    private static final String DIR = "words";
    private static final String FILE = "words.txt";

    static {
        // @formatter:off
        COUNTS1.put("one", 3); COUNTS1.put("two", 2); COUNTS1.put("three", 1);
        COUNTS2.put("five", 2); COUNTS2.put("six", 1); COUNTS2.put("seven123", 1);
        // @formatter:on
    }

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { asList(TEXT1), asList(COUNTS1) }, 
            { asList(TEXT2), asList(COUNTS2) }, 
            { asList(TEXT1, TEXT2), asList(COUNTS1, COUNTS2) }, 
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
        counter = new WordCounter();
    }

    @Test
    public void testCountWordsString() {
        Map<String, Integer> result = counter.countWords(text(texts));
        assertEquals(combine(countsList), result);
    }

    @Test
    public void testCountWordsFile() throws Exception {
        Map<String, Integer> result = counter.countWords(file(texts));
        assertEquals(combine(countsList), result);
    }

    @Test
    public void testCountWordsDirectoryTree() throws Exception {
        Map<String, Integer> result = counter.countWords(tree(texts));
        assertEquals(combine(countsList), result);
    }

    private static String text(List<String> texts) {
        StringBuilder s = new StringBuilder();
        for (String text : texts) {
            s.append(text + "\n");
        }
        return s.toString();
    }

    private static File file(List<String> texts) throws IOException {
        File file = new File(FILE);
        FileUtils.writeStringToFile(file, text(texts));
        return file;
    }

    private static File tree(List<String> texts) throws IOException {
        File dir = new File(DIR);
        deleteDir(dir);
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

    private static void deleteDir(File dir) {
        while (dir.exists()) {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
            }
        }
    }

    private static Map<String, Integer> combine(List<Map<String, Integer>> countsList) {
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Integer> counts : countsList) {
            WordCounter.addCounts(result, counts);
        }
        return result;
    }
}
