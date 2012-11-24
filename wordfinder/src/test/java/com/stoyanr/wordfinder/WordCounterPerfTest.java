package com.stoyanr.wordfinder;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.stoyanr.util.Logger;

@RunWith(Parameterized.class)
public class WordCounterPerfTest {

    private static final String[] VOCABULARY = { "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen" };

    private static final String DELIMS = WordCounter.DEFAULT_DELIMITERS
        + ";,.:?!/\\'\"()[]{}<>+-*=~@#$%^&|`";

    private static final String DIR = "words";
    private static final String FILE = "words.txt";

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { 100, 100000 }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final int numFiles;
    private final int maxWords;

    private WordCounter counter;

    public WordCounterPerfTest(int numFiles, int maxWords) {
        this.numFiles = numFiles;
        this.maxWords = maxWords;
    }

    @Before
    public void setUp() {
        Logger.level = Logger.Level.INFO;
        counter = new WordCounter(DELIMS, 4);
    }

    @Test
    public void test() throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        File tree = tree(counts);
        System.out.printf("Processing %d files ...\n", numFiles);
        long time0 = System.currentTimeMillis();
        Map<String, Integer> countsx = counter.countWords(tree);
        long time1 = System.currentTimeMillis();
        System.out.printf("Processed %d files in %d ms\n", numFiles, (time1 - time0));
        printCounts(countsx);
        TestUtils.assertEqualMaps(counts, countsx);
    }

    private File tree(Map<String, Integer> counts) throws IOException {
        File dir = new File(DIR);
        TestUtils.deleteDir(dir);
        dir.mkdirs();
        for (int i = 0; i < numFiles; i++) {
            File dirx = new File(DIR + "/" + i);
            dirx.mkdirs();
            FileUtils.writeStringToFile(new File(DIR + "/" + i + "/" + FILE), text(counts));
        }
        return dir;
    }

    private String text(Map<String, Integer> counts) {
        int numWords = (int) (Math.random() * maxWords);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numWords; i++) {
            int index = (int) (Math.random() * VOCABULARY.length);
            String word = VOCABULARY[index];
            sb.append(word);
            appendDelimiters(sb);
            counts.put(word, (counts.containsKey(word)) ? counts.get(word) + 1 : 1);
        }
        return sb.toString();
    }

    private void appendDelimiters(StringBuilder sb) {
        int numDelims = (int) (Math.random() * 3) + 1;
        for (int i = 0; i < numDelims; i++) {
            int index = (int) (Math.random() * DELIMS.length());
            sb.append(DELIMS.charAt(index));
        }
    }
    
    private void printCounts(Map<String, Integer> counts) {
        for (Entry<String, Integer> e : counts.entrySet()) {
            String word = e.getKey();
            int count = e.getValue();
            System.out.printf("%12s: %d\n", word, count);
        }
    }

}
