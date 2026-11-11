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
package ghidra.app.script;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import generic.test.AbstractGenericTest;
import ghidra.util.classfinder.ClassSearcher;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.ConsoleTaskMonitor;

public class GhidraScriptUtilTest extends AbstractGenericTest {

	@Before
	public void setup() throws CancelledException {
		ClassSearcher.search(new ConsoleTaskMonitor());
	}

	@Test
	public void fixupName_WithExtension() {
		String input = "Bob.java";
		assertEquals("Bob.java", GhidraScriptUtil.fixupName(input));
	}

	@Test
	public void fixupName_WithoutExtension() {
		String input = "Bob";
		assertEquals("Bob.java", GhidraScriptUtil.fixupName(input));
	}

	@Test
	public void fixupName_WithPackageDots() {
		String input = "a.b.c.Bob";
		assertEquals("a/b/c/Bob.java", GhidraScriptUtil.fixupName(input));
	}

	@Test
	public void fixupName_WithPackageSlashes() {
		String input = "a/b/c/Bob";
		assertEquals("a/b/c/Bob.java", GhidraScriptUtil.fixupName(input));
	}

	@Test
	public void fixupName_InnerClass() {
		String input = "Bob$InnerClass";
		assertEquals("Bob.java", GhidraScriptUtil.fixupName(input));
	}

	@Test
	public void fixupName_InnerClass_WithPackageDots() {
		String input = "a.b.c.Bob$InnerClass";
		assertEquals("a/b/c/Bob.java", GhidraScriptUtil.fixupName(input));
	}

	@Test
	public void fixupName_Python() {
		String input = "Bob.py";
		assertEquals("Bob.py", GhidraScriptUtil.fixupName(input));
	}

}
