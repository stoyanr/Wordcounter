/*
 * $Id: $
 *
 * Copyright 2012 Stoyan Rachev (stoyanr@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stoyanr.wordcounter;

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
