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

import javax.swing.*;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;

/**
 * Interface for the model for {@link SearchList}. It is an extension of a JList's model to add
 * the ability to group items into categories.
 *
 * @param <T> the type of data items in the search list
 */
public interface SearchListModel<T> extends ListModel<SearchListEntry<T>> {

	/**
	 * Returns the list of categories in the order they were added to the model
	 * @return the list of categories in the order they were added to the model
	 */
    List<String> getCategories();

	/**
	 * Sets the filter for the model data to display.
	 * @param filter the BiPredicate for the model data to display which will filter based on
	 * the item and its category
	 */
    void setFilter(BiPredicate<T, String> filter);

    /**
     * Sets an optional ranking used to order items within each category; lower scores are shown
     * first and equal scores keep their original order.
     *
     * @param ranking the scoring function (item, category), or null to keep the original order
     */
    default void setRanking(ToIntBiFunction<T, String> ranking) {
        // optional
    }

	/**
	 * Clean up any resources held by the model
	 */
    void dispose();

}
