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

import com.stoyanr.util.CharPredicate;

public class WordUtils {
    
    public static WordCounts countWords(String text, CharPredicate pred) {
        assert (text != null);
        WordCounts result = new WordCounts();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && !pred.test(text.charAt(i))) {
                i++;
            }
            int bi = i;
            while (i < text.length() && pred.test(text.charAt(i))) {
                i++;
            }
            int ei = i;
            if (bi != ei) {
                result.add(text.substring(bi, ei), 1);
            }
        }
        return result;
    }
    
    public static int getEndWordIndex(String text, CharPredicate pred) {
        int ei = text.length();
        while (ei > 0 && pred.test(text.charAt(ei - 1))) {
            ei--;
        }
        return ei;
    }
}
