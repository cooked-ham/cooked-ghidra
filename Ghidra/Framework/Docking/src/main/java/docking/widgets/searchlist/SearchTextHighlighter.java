/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package docking.widgets.searchlist;

import ghidra.util.HTMLUtilities;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Escapes result text and emphasizes literal query words; caches the current query pattern.
 */
public class SearchTextHighlighter {
    private String query;
    private Pattern pattern;

    public String highlight(String text, String filter) {
        String normalized = filter == null ? "" : filter.strip();
        if (!normalized.equals(query)) {
            query = normalized;
            pattern = query.isEmpty() ? null : Pattern.compile(Arrays.stream(query.split("\\s+"))
                            .map(Pattern::quote).collect(Collectors.joining("|")),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        if (pattern == null) {
            return HTMLUtilities.escapeHTML(text);
        }
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int start = 0;
        while (matcher.find()) {
            result.append(HTMLUtilities.escapeHTML(text.substring(start, matcher.start())));
            result.append("<b>").append(HTMLUtilities.escapeHTML(matcher.group())).append("</b>");
            start = matcher.end();
        }
        return result.append(HTMLUtilities.escapeHTML(text.substring(start))).toString();
    }
}
