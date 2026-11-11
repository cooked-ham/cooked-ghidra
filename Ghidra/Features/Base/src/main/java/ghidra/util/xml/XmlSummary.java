/* ###
 * IP: GHIDRA
 * REVIEWED: YES
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
package ghidra.util.xml;

class XmlSummary {

    static String getSummary(Counter counter) {
        StringBuffer buffer = new StringBuffer(256);

		int origTotal = counter.getTotalCount();

        buffer.append("\n");
        buffer.append("\n"+"XML Program Summary:");
        buffer.append("\n"+"--------------------------");
        buffer.append("\n").append("Memory Sections:       ").append(counter.getCountAndRemove("MEMORY_SECTION"));
        buffer.append("\n").append("Memory Contents:       ").append(counter.getCountAndRemove("MEMORY_CONTENTS"));
        buffer.append("\n").append("Code Blocks:           ").append(counter.getCountAndRemove("CODE_BLOCK"));
        buffer.append("\n").append("Defined Data:          ").append(counter.getCountAndRemove("DEFINED_DATA"));
        buffer.append("\n").append("Structures:            ").append(counter.getCountAndRemove("STRUCTURE"));
        buffer.append("\n").append("Unions:                ").append(counter.getCountAndRemove("UNION"));
        buffer.append("\n").append("Typedefs:              ").append(counter.getCountAndRemove("TYPE_DEF"));
        buffer.append("\n").append("Enums:                 ").append(counter.getCountAndRemove("ENUM"));
        buffer.append("\n").append("Symbols:               ").append(counter.getCountAndRemove("SYMBOL"));
        buffer.append("\n").append("Entry Points:          ").append(counter.getCountAndRemove("PROGRAM_ENTRY_POINT"));
        buffer.append("\n").append("Equates:               ").append(counter.getCountAndRemove("EQUATE"));
        buffer.append("\n").append("    References:        ").append(counter.getCountAndRemove("EQUATE_REFERENCE"));
        buffer.append("\n").append("Comments:              ").append(counter.getCountAndRemove("COMMENT"));
		buffer.append("\n").append("Bookmarks:             ").append(counter.getCountAndRemove("BOOKMARK"));
        buffer.append("\n").append("Properties:            ").append(counter.getCountAndRemove("PROPERTY"));
        buffer.append("\n").append("Program Trees:         ").append(counter.getCountAndRemove("TREE"));
        buffer.append("\n").append("    Folders:           ").append(counter.getCountAndRemove("FOLDER"));
        buffer.append("\n").append("    Fragments:         ").append(counter.getCountAndRemove("FRAGMENT"));
		buffer.append("\n").append("Function Signatures:   ").append(counter.getCountAndRemove("FUNCTION_DEF"));
		buffer.append("\n").append("    Parameters:        ").append(counter.getCountAndRemove("PARAMETER"));
        buffer.append("\n").append("Functions:             ").append(counter.getCountAndRemove("FUNCTION"));
        buffer.append("\n").append("    Stack Frames:      ").append(counter.getCountAndRemove("STACK_FRAME"));
        buffer.append("\n").append("    Stack Vars:        ").append(counter.getCountAndRemove("STACK_VAR"));
        buffer.append("\n").append("    Register Vars:     ").append(counter.getCountAndRemove("REGISTER_VAR"));
        buffer.append("\n"+"References:            " + counter.getCountAndRemove("MEMORY_REFERENCE")
                                                     + counter.getCountAndRemove("STACK_REFERENCE")
                                                     + counter.getCountAndRemove("EXT_LIBRARY_REFERENCE"));
        buffer.append("\n").append("Relocations:           ").append(counter.getCountAndRemove("RELOCATION"));
		buffer.append("\n");

		counter.getCountAndRemove("MEMBER");//remove from overhead...

		buffer.append("\n"+"--------------------------");
		buffer.append("\n").append("Total XML Elements:    ").append(origTotal);
		buffer.append("\n").append("    Processed:         ").append((origTotal-counter.getTotalCount()));
		buffer.append("\n").append("    Overhead:          ").append(counter.getTotalCount());
        buffer.append("\n");

		counter.clear();

        return buffer.toString();
    }

}
