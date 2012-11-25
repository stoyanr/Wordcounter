[Introduction](#Introduction) | [Overview](#Overview) | [Command Line Interface](#CommandLineInterface) | [Design](#Design) | [References](#References)

## <a id="Introduction"></a>Introduction

**Wordcounter** is a Java library and command-line utility for counting words in text files and directory trees and performing analysis on the word counts, such as finding the top X most used words in all files. It makes heavy use of parallel computing to use all available cores when performing the analysis.

The library uses JDK 8 [lambdas](http://openjdk.java.net/projects/lambda/), as well as new JDK 7 features such as [Fork / Join](http://docs.oracle.com/javase/tutorial/essential/concurrency/forkjoin.html) and [NIO.2](http://docs.oracle.com/javase/tutorial/essential/io/fileio.html). It is built with the [early access version of JDK 8 with lambda support](http://jdk8.java.net/lambda/).

With the introduction of lambdas and their supporting features in JDK 8, the way we build software in Java is going to change. If you would like to get an idea how your Java code may look like in a few years, you may take a look at Wordcounter. Unlike most resources available at the moment, this is not a tutorial, but a real working project.

The latest binary, javadoc, and sources packages can be found in [downloads](https://github.com/downloads/stoyanr/Wordcounter/):
+ [wordcounter-1.0.jar](https://github.com/downloads/stoyanr/Wordcounter/wordcounter-1.0-javadoc.jar)
+ [wordcounter-1.0-javadoc.jar](https://github.com/downloads/stoyanr/Wordcounter/wordcounter-1.0-javadoc.jar)
+ [wordcounter-1.0-sources.jar](https://github.com/downloads/stoyanr/Wordcounter/wordcounter-1.0-sources.jar)

This work is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

**Feedback, comments, and contributions are welcome!**

## <a id="Overview"></a>Overview

### Library Features

+ Find the usage of all words in a string, a single text file, or a directory tree containing text files.
+ Analyze the word usages to find the top X most used or the bottom X least used words.
+ Choose between non-parallel and parallel implementations to compare their performance.

## Programming Highlights

+ Uses [Producer / Consumer](http://en.wikipedia.org/wiki/Producer-consumer_problem) for counting the words in parallel threads while reading them. A single producer task reads all files to strings and puts them in a `BlockingQueue`. Multiple consumer threads take strings from the queue and count their words, accumulating the result in a `ConcurrentHashMap`.
+ Uses [Fork / Join](http://docs.oracle.com/javase/tutorial/essential/concurrency/forkjoin.html) for performing analysis on the word counts. With large maps, a `RecursiveTask` forks itself until reaching the desired concurrency level and then joins the results.
+ Uses [NIO.2](http://docs.oracle.com/javase/tutorial/essential/io/fileio.html) for traversing directory trees and reading files.
+ Makes heavy use of functional interfaces and [lambdas](http://openjdk.java.net/projects/lambda/) in order to pass functions rather than data when appropriate.  
+ There are comprehensive **unit and performance tests** for the two most important classes. 
+ As usual, the code is **clean, well-structured, and easy to read**. Formatting, naming, and comments are uniform and consistent. A lot of attention has been put in the appropriate use of both object-oriented and functional programming techniques.

### History

Similarly to [Feeder](https://github.com/stoyanr/Feeder), [Feeder](https://github.com/stoyanr/Todor), and [Hanoier](https://github.com/stoyanr/Hanoier), Wordcounter was originally created in November 2012 for an internal "geeks" contest at [SAP](http://www.sap.com). The contest task requested to implement an algorithm using Fork / Join and lambdas that analyzes all files in a directory and finds the ten most used words in the files together with how many times they occur. Rather than simply sticking with Fork / Join, I attempted to find the most suitable parallel approach for this task, which led me to some interesting discoveries.

## <a id="CommandLineInterface"></a>Command Line Interface

To start the command line program, execute the following command:

```
java -jar wordcounter-1.0.jar <options>
```

All options have reasonable default values so none of them is mandatory. Using the defaults for all options results in finding the top 10 most used words in the current directory and its subdirectories.

Options:
+ `-p <path>` The file or directory to search, default is ".".
+ `-d <delimiters>` The set of delimiters to use, default is `" \t\n\r\f;,.:?!/\\'\"()[]{}<>+-*=~@#$%^&|"`.
+ `-n <number>` The number of most or least used words to find, default is 10. Use 0 for all encountered words.
+ `-l [error|warning|info|debug]` The log level to use, default is "info". 
+ `-m [top|bottom]` The mode, "top" stands for finding the most used words, "bottom" stands for finding the least used words.

Examples:
+ Find the top 10 most used words in the directory "root": `-p root`
+ Find the bottom 5 least used words in the directory "rootx" with debug information: `-p rootx -n 5 -m bottom -l debug`

## <a id="Design"></a>Design

### The WordCounter Class

The `WordCounter` class provides methods for counting words in strings, files, and directory trees. It uses a Producer / Consumer parallel implementation for doing this in the most efficient way. The two internal classes `Reader` and `Counter` provide the implementation of the producer and consumer tasks. These tasks are created using lambdas in the following way:

```java
private ScheduledExecutorService createReaders(final File file, final BlockingQueue<String> queue) {
    ScheduledExecutorService readers = new ScheduledThreadPoolExecutor(1);
    readers.submit(new Reader(file, queue, this)::read);
    return readers;
}
```

The construct `new Reader(file, queue, this)::read` is a *method reference* of type `Runnable` which refers to the `read` method of the newly created `Reader` object.

See:
+ [WordCounter.java](blob/master/wordcounter/src/com/stoyanr/wordcounter/WordCounter.java)
+ [Reader.java](blob/master/wordcounter/src/com/stoyanr/wordcounter/Reader.java)
+ [Counter.java](blob/master/wordcounter/src/com/stoyanr/wordcounter/Counter.java)

### The WordCountAnalyzer Class

The `WordCountAnalyzer` class provides methods for performing analysis on the word counts produced by `WordCounter`. The differnet analysis operations implement the internal `AnalysisOperation` interface, which provides methods for getting instances of two functional interfaces, `Analyzer` and `Merger` respectively. The `Analyzer` interface provides the core analysis method, while the `Merger` interface allows to merge the results of two analyses performed in parallel threads into one.

```java
interface AnalysisOperation<T> {
    interface Analyzer<T> {
        T analyze(int lo, int hi);
    }
    
    interface Merger<T> {
        T merge(T result1, T result2);
    }    
    
    Analyzer<T> getAnalyzer();

    Merger<T> getMerger();
}
```

For the moment, the only analysis operation available is `FindTopOperation`, which finds the top or bottom used words. This class implements the `getAnalyzer` and `getMerger` methods again using lambdas:

```java
@Override
public Analyzer<SortedMap<Integer, Set<String>>> getAnalyzer() {
    return (lo, hi) -> findTop(lo, hi);
}
```

The construct `(lo, hi) -> findTop(lo, hi)` is a *lambda expression* of type `Analyzer<SortedMap<Integer, Set<String>>>` which invokes the `findTop` method of the operation.

`WordCountAnalyzer` is a pretty generic Fork / Join processor. The execution is triggerred by passing an instance of the internal class `AnalyzerTask` initialized with the analyzer and merger of the current operation to a fork / join pool:

```java
private <T> T execute(AnalysisOperation<T> op, int size, boolean parallel) {
    if (parallel) {
        return forkJoinPool.invoke(new AnalyzerTask<>(0, size, size, op.getAnalyzer(),
            op.getMerger()));
    } else {
        return op.getAnalyzer().analyze(0, size);
    }
}
```

The `AnalyzerTask` itself is a `RecursiveTask` with an almost classical `compute` method:

```java
@Override
protected T compute() {
    T result;
    if (hi - lo <= Math.max(size / PAR, MIN_THRESHOLD)) {
        result = analyzer.analyze(lo, hi);
    } else {
        int mid = (lo + hi) >>> 1;
        AnalyzerTask<T> t1 = new AnalyzerTask<>(lo, mid, size, analyzer, merger);
        t1.fork();
        AnalyzerTask<T> t2 = new AnalyzerTask<>(mid, hi, size, analyzer, merger);
        T result2 = t2.compute();
        T result1 = t1.join();
        result = merger.merge(result1, result2);
    }
    return result;
}
```
See:
+ [WordCountAnalyzer.java](blob/master/wordcounter/src/com/stoyanr/wordcounter/WordCountAnalyzer.java)
+ [AnalysisOperation.java](blob/master/wordcounter/src/com/stoyanr/wordcounter/AnalysisOperation.java)
+ [FindTopOperation.java](blob/master/wordcounter/src/com/stoyanr/wordcounter/FindTopOperation.java)

