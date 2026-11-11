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
package ghidra.program.model.data;

import static org.junit.Assert.*;

import org.junit.*;

import generic.test.AbstractGenericTest;
import ghidra.program.database.data.TransientDataTypeManager;

public class FunctionDefinitionDataTypeTest extends AbstractGenericTest {

	private DataTypeManager dtm;
	private FunctionDefinition functionDt;

	private FunctionDefinition createFunctionDefinition(String name) {
		return (FunctionDefinition) dtm.resolve(new FunctionDefinitionDataType(name), null);
	}

	@Before
	public void setUp() throws Exception {
		dtm = new TransientDataTypeManager("dummyDTM");
		dtm.startTransaction("");
		functionDt = createFunctionDefinition("Test");
	}

	@After
	public void tearDown() {
		dtm.close();
	}

	@Test
	public void testConstructor_WithName() {
		FunctionDefinitionDataType impl;
		FunctionDefinition fdt;

		// name
		impl = new FunctionDefinitionDataType("testFunctionDefinition");
		assertEquals("testFunctionDefinition", impl.getName());
		assertNull(impl.getComment());
		assertTrue(impl.getReturnType().isEquivalent(DataType.DEFAULT));
		assertEquals(0, impl.getArguments().length);
		assertNull(impl.getDataTypeManager());
		assertEquals("undefined testFunctionDefinition(void)",
			impl.getMnemonic(null));
		assertEquals("/testFunctionDefinition", impl.getPathName());

		fdt = (FunctionDefinition) dtm.resolve(impl, null);
		assertEquals("testFunctionDefinition", fdt.getName());
		assertNull(fdt.getComment());
		assertTrue(fdt.getReturnType().isEquivalent(DataType.DEFAULT));
		assertEquals(0, fdt.getArguments().length);
		String fdtCat = fdt.getCategoryPath().getPath();
		String rootCat = CategoryPath.ROOT.getPath();
		assertEquals(fdtCat, rootCat);
		assertEquals(fdt.getDataTypeManager(), dtm);
		assertEquals("undefined testFunctionDefinition(void)",
			fdt.getMnemonic(null));
		assertEquals("/testFunctionDefinition", fdt.getPathName());
	}

	@Test
	public void testConstructor_WithDtmName() throws Exception {
		FunctionDefinitionDataType impl;
		FunctionDefinition fdt;

		// dtm, name
		impl = new FunctionDefinitionDataType("testFunctionDefinition");
		assertEquals("testFunctionDefinition", impl.getName());
		assertNull(impl.getComment());
		assertTrue(impl.getReturnType().isEquivalent(DataType.DEFAULT));
		assertEquals(0, impl.getArguments().length);
		assertEquals(CategoryPath.ROOT, impl.getCategoryPath());
		assertEquals("undefined testFunctionDefinition(void)",
			impl.getMnemonic(null));
		assertEquals("/testFunctionDefinition", impl.getPathName());

		fdt = (FunctionDefinition) dtm.resolve(impl, null);
		assertEquals("testFunctionDefinition", fdt.getName());
		assertNull(fdt.getComment());
		assertTrue(fdt.getReturnType().isEquivalent(DataType.DEFAULT));
		assertEquals(0, fdt.getArguments().length);
		String fdtCat = fdt.getCategoryPath().getPath();
		String rootCat = CategoryPath.ROOT.getPath();
		assertEquals(fdtCat, rootCat);
		assertEquals(fdt.getDataTypeManager(), dtm);
		assertEquals("undefined testFunctionDefinition(void)",
			fdt.getMnemonic(null));
		assertEquals("/testFunctionDefinition", fdt.getPathName());
	}

	@Test
	public void testConstructor_WithFuncSig() {
		FunctionDefinitionDataType impl;
		FunctionDefinition fdt;
		FunctionDefinitionDataType sig = new FunctionDefinitionDataType("testFunctionSig");
		sig.setReturnType(VoidDataType.dataType);
		// sig
		impl = new FunctionDefinitionDataType(sig);
		assertEquals("testFunctionSig", impl.getName());
		assertNull(impl.getComment());
		assertTrue(impl.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, impl.getArguments().length);
		assertNull(impl.getDataTypeManager());
		assertEquals("void testFunctionSig(void)", impl.getMnemonic(null));
		assertEquals("/testFunctionSig", impl.getPathName());

		fdt = (FunctionDefinition) dtm.resolve(impl, null);
		assertEquals("testFunctionSig", fdt.getName());
		assertNull(fdt.getComment());
		assertTrue(fdt.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, fdt.getArguments().length);
		String fdtCat = fdt.getCategoryPath().getPath();
		String rootCat = CategoryPath.ROOT.getPath();
		assertEquals(fdtCat, rootCat);
		assertEquals(fdt.getDataTypeManager(), dtm);
		assertEquals("void testFunctionSig(void)", fdt.getMnemonic(null));
		assertEquals("/testFunctionSig", fdt.getPathName());
	}

	@Test
	public void testConstructor_WithDtmFuncSig() {
		FunctionDefinitionDataType impl;
		FunctionDefinition fdt;
		FunctionDefinitionDataType sig = new FunctionDefinitionDataType("testFunctionSig");
		sig.setReturnType(VoidDataType.dataType);

		// dtm, sig
		impl = new FunctionDefinitionDataType(sig);
		assertEquals("testFunctionSig", impl.getName());
		assertNull(impl.getComment());
		assertTrue(impl.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, impl.getArguments().length);
		assertEquals("void testFunctionSig(void)", impl.getMnemonic(null));
		assertEquals("/testFunctionSig", impl.getPathName());

		fdt = (FunctionDefinition) dtm.resolve(impl, null);
		assertEquals("testFunctionSig", fdt.getName());
		assertNull(fdt.getComment());
		assertTrue(fdt.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, fdt.getArguments().length);
		String fdtCat = fdt.getCategoryPath().getPath();
		String rootCat = CategoryPath.ROOT.getPath();
		assertEquals(fdtCat, rootCat);
		assertEquals(fdt.getDataTypeManager(), dtm);
		assertEquals("void testFunctionSig(void)", fdt.getMnemonic(null));
		assertEquals("/testFunctionSig", fdt.getPathName());
	}

	@Test
	public void testConstructor_WithDtmNameFuncSig() {
		FunctionDefinitionDataType impl;
		FunctionDefinition fdt;
		FunctionDefinitionDataType sig = new FunctionDefinitionDataType("testFunctionSig");
		sig.setReturnType(VoidDataType.dataType);

		// dtm, name, sig
		impl = new FunctionDefinitionDataType(CategoryPath.ROOT, "testDtmNameSig", sig);
		assertEquals("testDtmNameSig", impl.getName());
		assertNull(impl.getComment());
		assertTrue(impl.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, impl.getArguments().length);
		assertEquals("void testDtmNameSig(void)", impl.getMnemonic(null));
		assertEquals("/testDtmNameSig", impl.getPathName());

		fdt = (FunctionDefinition) dtm.resolve(impl, null);
		assertEquals("testDtmNameSig", fdt.getName());
		assertNull(fdt.getComment());
		assertTrue(fdt.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, fdt.getArguments().length);
		String fdtCat = fdt.getCategoryPath().getPath();
		String rootCat = CategoryPath.ROOT.getPath();
		assertEquals(fdtCat, rootCat);
		assertEquals(fdt.getDataTypeManager(), dtm);
		assertEquals("void testDtmNameSig(void)", fdt.getMnemonic(null));
		assertEquals("/testDtmNameSig", fdt.getPathName());
	}

	@Test
	public void testConstructor_WithFunctionDefDt() {
		FunctionDefinitionDataType impl;
		FunctionDefinition fdt;
		FunctionDefinitionDataType sig = new FunctionDefinitionDataType("testFunctionSig");
		sig.setReturnType(VoidDataType.dataType);
		FunctionDefinitionDataType functionDefDt = new FunctionDefinitionDataType(sig);

		// functionDataTypeImpl
		impl = new FunctionDefinitionDataType(functionDefDt);
		assertEquals("testFunctionSig", impl.getName());
		assertNull(impl.getComment());
		assertTrue(impl.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, impl.getArguments().length);
		assertNull(null, impl.getDataTypeManager());
		assertEquals("void testFunctionSig(void)", impl.getMnemonic(null));
		assertEquals("/testFunctionSig", impl.getPathName());

		fdt = (FunctionDefinition) dtm.resolve(impl, null);
		assertEquals("testFunctionSig", fdt.getName());
		assertNull(fdt.getComment());
		assertTrue(fdt.getReturnType().isEquivalent(VoidDataType.dataType));
		assertEquals(0, fdt.getArguments().length);
		String fdtCat = fdt.getCategoryPath().getPath();
		String rootCat = CategoryPath.ROOT.getPath();
		assertEquals(fdtCat, rootCat);
		assertEquals(fdt.getDataTypeManager(), dtm);
		assertEquals("void testFunctionSig(void)", fdt.getMnemonic(null));
		assertEquals("/testFunctionSig", fdt.getPathName());
	}

	@Test
	public void testSetArguments() throws Exception {
		ParameterDefinition[] parms = functionDt.getArguments();
		assertEquals(0, parms.length);
		addThreeArguments();
		parms = functionDt.getArguments();
		assertEquals(3, parms.length);
		assertTrue(parms[0].getDataType().isEquivalent(new ByteDataType()));
		assertTrue(parms[1].getDataType().isEquivalent(new FloatDataType()));
		assertTrue(parms[2].getDataType().isEquivalent(new CharDataType()));
		assertEquals("parm1", parms[0].getName());
		assertEquals("parm2", parms[1].getName());
		assertEquals("parm3", parms[2].getName());
		assertEquals("this is first parm.", parms[0].getComment());
		assertEquals("this is second parm.", parms[1].getComment());
		assertEquals("this is third parm.", parms[2].getComment());
	}

	private void addThreeArguments() {
		ParameterDefinition[] newParms = new ParameterDefinition[3];
		newParms[0] =
			new ParameterDefinitionImpl("parm1", new ByteDataType(), "this is first parm.");
		newParms[1] =
			new ParameterDefinitionImpl("parm2", new FloatDataType(), "this is second parm.");
		newParms[2] =
			new ParameterDefinitionImpl("parm3", new CharDataType(), "this is third parm.");
		functionDt.setArguments(newParms);
	}

	@Test
	public void testSetName() throws Exception {
		functionDt.setName("printf");
		assertEquals("printf", functionDt.getName());
	}

	@Test
	public void testSetComment() throws Exception {
		functionDt.setComment("My test comment.");
		assertEquals("My test comment.", functionDt.getComment());
	}

	@Test
	public void testSetReturnType() {
		functionDt.setReturnType(new DWordDataType());
		assertTrue(functionDt.getReturnType().isEquivalent(new DWordDataType()));
	}

	@Test
	public void testConflictsWithSpecialName() {

		String name = "operator[]";

		// Create two function definition datatypes with the same name but different signature
		FunctionDefinitionDataType impl1 = new FunctionDefinitionDataType(name);
		impl1.setReturnType(IntegerDataType.dataType);
		impl1.setArguments(new ParameterDefinition[] {
			new ParameterDefinitionImpl("x", IntegerDataType.dataType, null) });
		FunctionDefinitionDataType impl2 = new FunctionDefinitionDataType(name);
		impl2.setReturnType(VoidDataType.dataType);

		// Resolve the two function definitions.  They should not be equal, and the second
		// one should be a conflict
		FunctionDefinition fd1 = (FunctionDefinition) dtm.resolve(impl1, null);
		FunctionDefinition fd2 = (FunctionDefinition) dtm.resolve(impl2, null);
		Assert.assertNotEquals(fd1, fd2);
		assertTrue(fd2.getPrototypeString().contains(DataType.CONFLICT_SUFFIX));

		// Resolve the second function definition again.  It should not have created a new
		// function definition.
		FunctionDefinition fd3 = (FunctionDefinition) dtm.resolve(impl2, null);
		assertEquals(fd2, fd3);
	}
}
