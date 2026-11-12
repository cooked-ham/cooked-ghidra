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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SearchRankingTest {

    @Test
    public void testRankingOrdersWithinCategoryAndIsStable() {
        DefaultSearchListModel<String> model = new DefaultSearchListModel<>();
        model.add("A", List.of("Reopen file", "Open file", "Open recent", "Zoom open"));
        model.add("B", List.of("Reopen b", "Open b"));
        model.setRanking((s, c) -> {
            String n = s.toLowerCase();
            return n.startsWith("open") ? 0 : n.contains(" open") ? 1 : 2;
        });
        model.setFilter((s, c) -> s.toLowerCase().contains("open"));

        List<String> shown = model.getDisplayedItems().stream().map(e -> e.value()).toList();
        assertEquals(List.of("Open file", "Open recent", "Zoom open", "Reopen file", "Open b",
                "Reopen b"), shown);
        // categories keep their order
        assertEquals("A", model.getDisplayedItems().get(0).category());
        assertEquals(List.of("A", "B"), model.getCategories());

        model.setRanking(null);
        model.setFilter((s, c) -> s.toLowerCase().contains("open"));
        assertEquals("Reopen file", model.getDisplayedItems().get(0).value());
    }
}
