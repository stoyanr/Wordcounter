## <a id="Introduction"></a>Introduction

**Wordcounter** is a Java library and command-line utility for counting words in text files and directory trees and performing analysis on the word counts, such as finding the top N most used words in all files. It makes heavy use of functional programming constructs and parallel computing approaches to utilize all available cores when performing the analysis.

The library uses JDK 8 [lambdas](http://openjdk.java.net/projects/lambda/), as well as new JDK 7 features such as [Fork / Join](http://docs.oracle.com/javase/tutorial/essential/concurrency/forkjoin.html) and [NIO.2](http://docs.oracle.com/javase/tutorial/essential/io/fileio.html). It is built and can only be used with the [early access version of JDK 8 with lambda support](http://jdk8.java.net/lambda/).

With the introduction of lambdas and their supporting features in JDK 8, the way we build software in Java is going to change. If you would like to get an idea how your Java code might look like in a few years, you may take a look at Wordcounter. Unlike most resources available at the moment, this is not a tutorial, but a real working project.

The latest binary, javadoc, and sources packages can be found in [downloads](https://github.com/downloads/stoyanr/Wordcounter/):
+ [wordcounter-1.0.4.jar](https://github.com/downloads/stoyanr/Wordcounter/wordcounter-1.0.4.jar)
+ [wordcounter-1.0.4-javadoc.jar](https://github.com/downloads/stoyanr/Wordcounter/wordcounter-1.0.4-javadoc.jar)
+ [wordcounter-1.0.4-sources.jar](https://github.com/downloads/stoyanr/Wordcounter/wordcounter-1.0.4-sources.jar)

This work is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## <a id="Overview"></a>Overview

### Library Features

+ Count all words in a string, a single text file, or a directory tree containing text files.
+ Analyze the word counts to find the top N most used words, the bottom N least used words, or the total word count.
+ Specify whether a character is a word character via an external predicate.
+ Specify an optional operation to be performed on words, for example converting to lower case, via an external operator.
+ Choose between non-parallel and parallel implementations to compare their performance.
+ Specify the parallelism level to be a value different from the number of cores, if you need to.

### Programming Highlights

+ Uses [Producer / Consumer](http://en.wikipedia.org/wiki/Producer-consumer_problem) for reading files and counting the words in each file in parallel. The actual mechanism is encapsulated in a generic, reusable implementation.
+ Uses [Fork / Join](http://docs.oracle.com/javase/tutorial/essential/concurrency/forkjoin.html) for performing analysis on the word counts. Here again the actual mechanism is encapsulated in a generic, reusable implementation.
+ Uses [NIO.2](http://docs.oracle.com/javase/tutorial/essential/io/fileio.html) for traversing directory trees and reading files.
+ Makes heavy use of [functional interfaces](http://www.lambdafaq.org/what-is-a-functional-interface/) and [lambdas](http://openjdk.java.net/projects/lambda/) in order to pass functions rather than data where appropriate.
+ There are comprehensive unit and performance tests for the two most important classes. 
+ As usual, the code is clean, well-structured, and easy to read. Formatting, naming, and comments are uniform and consistent. A lot of attention has been put to the appropriate use of both object-oriented and functional programming techniques.

### History

Similarly to [Feeder](https://github.com/stoyanr/Feeder), [Todor](https://github.com/stoyanr/Todor), and [Hanoier](https://github.com/stoyanr/Hanoier), Wordcounter was originally created in November 2012 for an internal "geeks" contest at [SAP](http://www.sap.com). The contest task requested to implement an algorithm using Fork / Join and lambdas that analyzes all files in a directory and finds the ten most used words in the files together with how many times they occur. Rather than simply sticking with Fork / Join, I tried to find the most suitable parallel approach for this particular task, which led me to choose Producer / Consumer for the core word counting logic.

## <a id="CommandLineInterface"></a>Command Line Interface

To start the command line program, execute the following command:

```
java -jar wordcounter-1.0.4.jar <options>
```

All options have reasonable default values so none of them is mandatory. Using the defaults for all options results in finding the top 10 most used words in the current directory and its subdirectories.

Options:
+ `-p <path>` The file or directory to search, default is ".".
+ `-m [top|bottom|total]` The mode, "top" stands for finding the most used words, "bottom" stands for finding the least used words, and "total" stands for finding the total count of all words.
+ `-d <chars>` Additional characters (besides alphabetic characters) to consider as word characters, default is none. By default, only alphabetic characters are considered as word characters.
+ `-i` Ignore case when searching for words, by default the search is case-sensitive.
+ `-n <number>` The number of most or least used words to find, default is 10. 0 means all available words.
+ `-s` Use serial instead of parallel computation, by default the computation is parallel.
+ `-r <number>` The parallelism level t use, default is the number of available cores.
+ `-l [error|warning|info|debug]` The log level to use, default is "error". 

Examples:
+ Find the top 10 most used words in the directory "words": `-p words`
+ Find the bottom 5 least used words in the directory "wordsx", considering numbers as word characters, ignoring case, with info logging: `-p wordsx -m bottom -d 1234567890 -i -n 5 -l info`

## <a id="Design"></a>Design

The design of the library partitions the problem into generic parallel processing utilities, classes that encapsulate the data structures used to represent raw and sorted word counts, and finally classes that perform the counting and analysis using the capabilities of the previous two groups. Practically all of these classes use instances of functional interfaces quite a lot in order to allow specific customizations to their generic behavior. This results in code which is heavily infused with lambda expressions and method references. Welcome to the world of functional programming in Java!

### Generic Parallel Processing Utilities

#### The ForkJoinComputer Class

The `ForkJoinComputer<T>` class is a generic Fork / Join computer. It divides the initial size by 2 until either reaching the specified parallelism level or falling below the specified threshold, computes each portion serially using the specified `Computer<T>`, and then joins the results of all computations using the specified `Merger<T>`. Here, `Computer` and `Merger` are functional interfaces that are defined as follows:

```java
public interface Computer<T> {
    T compute(int lo, int hi);
}
    
public interface Merger<T> {
    T merge(T result1, T result2);
}
```

This class can be used by simply instantiating it with the appropriate lambdas and then calling its `compute` method.

```java
// Calculate the sum of all integers from 1 to n, using 1000 as a threshold
new ForkJoinComputer<Integer>(n, 1000, 
    (lo, hi) -> { int sum = 0; for (int i = lo + 1; i <= hi; i++) sum += i; return sum; }, 
    (a, b) -> a + b).compute();
```
See:
+ [ForkJoinComputer.java](Wordcounter/blob/master/wordcounter/src/main/java/com/stoyanr/util/ForkJoinComputer.java)

#### The ProducerConsumerExecutor Class

The `ProducerConsumerExecutor<T1, T2>` class is a generic Producer / Consumer executor. It starts a single `Producer<T1>` task and multiple `Mediator<T1, T2>` and `Consumer<T2>` tasks with their number equal to the specified parallelism level. The producer puts `T1` instances in a `BlockingQueue<T1>`. The mediators take these instances from there, convert them to `T2`, and put them in another blocking queue of type `BlockingQueue<T2>`. Finally, the consumers take the `T2` instances from the second blocking queue and process them.

Here, `Producer`, `Consumer`, and `Mediator` are functional interfaces that are defined as follows:

```java
public interface Producer<T> {
    void produce(Block<T> block);
}
    
public interface Consumer<T> {
    void consume(T t);
}
    
public interface Mediator<T1, T2> {
    void mediate(T1 t, Block<T2> block);
}
```

In the above code, `Block` is a standard function defined in `java.util.functions`. The block passed to the `Producer` and `Mediator` methods puts the produced data in the corresponding blocking queue. 

Similarly to `ForkJoinComputer`, this class can be used by simply instantiating it with the appropriate lambdas and then calling its `execute` method.

See:
+ [ProducerConsumerExecutor.java](Wordcounter/blob/master/wordcounter/src/main/java/com/stoyanr/util/ProducerConsumerExecutor.java)

### Data Structure Classes

These classes encapsulate the data structures used to represent raw and sorted word counts.
+ The `WordCounts` class represents a list of words mapped to their usage counts. It provides methods for adding word counts, checking for equality, printing, and internal iterations over its contents. Internally, this class encapsulates a `Map<String, AtomicInteger>` which is either a `HashMap` or a `ConcurrentHashMap` depending on the parallelism level specified upon construction. The word counting methods of `WordUtils` and `WordCounter` return instances of this class.
+ The `TopWordCounts` class represents a sorted list of word usage counts mapped to all words that have such counts. It provides methods for adding top word counts, checking for equality, and printing. Internally, this class encapsulates a `SortedMap<Integer, Set<String>>`. Some of the analysis methods of `WordCountAnalyzer` return instances of this class.

See:
+ [WordCounts.java](Wordcounter/blob/master/wordcounter/src/main/java/com/stoyanr/wordcounter/WordCounts.java)
+ [TopWordCounts.java](Wordcounter/blob/master/wordcounter/src/main/java/com/stoyanr/wordcounter/TopWordCounts.java)

### Word Counting and Analysis Classes

#### The WordUtils Utility Class

The `WordUtils` class is a utility class that provides several overloaded static methods for counting words in strings. The central method `countWords` accepts a string, a predicate to determine whether a character is a word character, and an optional unary operator to be performed on words. 

```java
// Count all words consisting of only alphabetic chars, ignoring case
WordCounts wc = WordUtils.countWords(text, (c) -> Character.isAlphabetic(c), (s) -> s.toLowerCase());
```

See:
+ [WordUtils.java](Wordcounter/blob/master/wordcounter/src/main/java/com/stoyanr/wordcounter/WordUtils.java)

#### The WordCounter Class

The `WordCounter` class provides a method for counting words in a `Path` representing a file or a directory tree, either serially or in parallel. It is initialized with a path, a predicate to determine whether a character is a word character, an optional unary operator to be performed on words, a flag indicating whether to use parallel processing, and (optionally) a parallelism level. It can be used by simply instantiating it with the appropriate lambdas and then calling its `count` method:

```java
// Count all words consisting of only alphabetic chars, ignoring case, using parallel processing
WordCounts wc = new WordCounter(path, (c) -> Character.isAlphabetic(c), (s) -> s.toLowerCase(), true).count();
```

The parallel implementation uses `ProducerConsumerExecutor<Path, String>`. The producer simply walks the directory tree and produces `Path` instances. The mediators read the files into text pieces, and the consumers count the words in each text piece and collect them in a single `WordCounts` instance. This is done with the following piece of code:

```java
private WordCounts countPar() {
    final WordCounts wc = new WordCounts(parLevel);
    new ProducerConsumerExecutor<Path, String>(
        (block) -> collectPaths(block), 
        (file, block) -> readFileToBlock(file, block),
        (text) -> wc.add(countWords(text, pred, op)), parLevel).execute();
    return wc;
}
```

See:
+ [WordCounter.java](Wordcounter/blob/master/wordcounter/src/main/java/com/stoyanr/wordcounter/WordCounter.java)

#### The WordCountAnalyzer Class

The `WordCountAnalyzer` class provides methods for performing analysis on the word counts produced by `WordCounter`, such as finding the top N most used words. It is initialized with a `WordCounts` instance, a flag indicating whether to use parallel processing, and (optionally) a parallelism level. It can be used by simply instantiating it and then calling one of its methods such as `findTop` or `total`:

```java
// Find the top 10 most used words in wc
TopWordCounts twc = new WordCountAnalyzer(wc, true).findTop(10, (x, y) -> (y - x));
```

The different analysis types implement the internal `Analysis<T>` interface, which is defined as follows:

```java
interface Analysis<T> {
    T compute(int lo, int hi);
    T merge(T r1, T r2);
}
```

Since the signatures of the above two methods mimic the `Computer` and `Merger` functional interfaces used by `ForkJoinComputer`, we can use fork / join for all analysis types in the following way:

```java
public TopWordCounts findTop(int number, Comparator<Integer> comparator) {
    return analyse(new FindTopAnalysis(number, comparator));
}

private <T> T analyse(Analysis<T> a) {
    if (par) {
        return new ForkJoinComputer<T>(wc.getSize(), THRESHOLD, a::compute, a::merge, parLevel).compute();
    } else {
        return a.compute(0, wc.getSize());
    }
}
```

For the moment, the only analysis types available are `FindTopAnalysis` and `TotalAnalysis`. They implement the above two methods via internal iteration by passing a lambda to the `forEachInRange` method of `WordCounts`:

```java
@Override
public TopWordCounts compute(int lo, int hi) {
    TopWordCounts result = new TopWordCounts(number, comparator);
    wc.forEachInRange(lo, hi, (word, count) -> result.addIfNeeded(count, word));
    return result;
}
```

See:
+ [WordCountAnalyzer.java](Wordcounter/blob/master/wordcounter/src/main/java/com/stoyanr/wordcounter/WordCountAnalyzer.java)

## Performance

I found out that the parallel Producer / Consumer word counting implementation is adapting nicely to the different number of cores and I/O speeds. It is significantly faster than the serial implementation. Unlike it, the parallel Fork / Join analysis implementation is only faster than the serial one when testing with an unrealistically large number of unique words, and only by a mild degree. With small number of unique words, it is actually *slower* than the serial one.

The tables below compare the performance of word counting and find top analysis under the following conditions:
+ CPU AMD Phenom II X4 965 3.4 GHz (4 cores), 4 GB RAM, Windows 7, JDK 8
+ Default options: words consisting of alphabetic characters, case-sensitive
+ Default parallelism level, equal to the number of cores

### Word Counting Performance

<table>
<thead>
<tr class="header">
<th align="left">Implementation</th>
<th align="right">Files</th>
<th align="right">Words</th>
<th align="right">Size (MB)</th>
<th align="right">Time (ms)</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td align="left">Serial</td>
<td align="right">1</td>
<td align="right">10000000</td>
<td align="right">~65</td>
<td align="right">2200 - 2400</td>
</tr>
<tr class="even">
<td align="left">Parallel</td>
<td align="right">1</td>
<td align="right">10000000</td>
<td align="right">~65</td>
<td align="right">500 - 600</td>
</tr>
<tr class="odd">
<td align="left">Serial</td>
<td align="right">100</td>
<td align="right">10000000</td>
<td align="right">~65</td>
<td align="right">1600 - 1800</td>
</tr>
<tr class="even">
<td align="left">Parallel</td>
<td align="right">100</td>
<td align="right">10000000</td>
<td align="right">~65</td>
<td align="right">500 - 600</td>
</tr>
</tbody>
</table>

### Find Top Analysis Performance

<table>
<thead>
<tr class="header">
<th align="left">Implementation</th>
<th align="right">Words</th>
<th align="right">Max Count</th>
<th align="right">Top</th>
<th align="right">Time (ms)</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td align="left">Serial</td>
<td align="right">2000000</td>
<td align="right">10000000</td>
<td align="right">10</td>
<td align="right">200 - 250</td>
</tr>
<tr class="even">
<td align="left">Parallel</td>
<td align="right">2000000</td>
<td align="right">10000000</td>
<td align="right">10</td>
<td align="right">200 - 250</td>
</tr>
</tbody>
</table>