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
package ghidra.app.plugin.core.decompile;

import static org.junit.Assert.*;

import org.junit.Test;

import docking.widgets.fieldpanel.support.FieldLocation;
import ghidra.app.cmd.equate.SetEquateCmd;
import ghidra.app.cmd.function.CreateFunctionCmd;
import ghidra.app.cmd.function.DeleteFunctionCmd;
import ghidra.app.cmd.label.RenameLabelCmd;
import ghidra.app.decompiler.ClangToken;
import ghidra.app.decompiler.ClangVariableToken;
import ghidra.app.decompiler.component.ClangTextField;
import ghidra.app.plugin.core.decompile.actions.IsolateVariableTask;
import ghidra.app.plugin.core.decompile.actions.RenameVariableTask;
import ghidra.framework.options.Options;
import ghidra.program.database.symbol.CodeSymbol;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.Undefined;
import ghidra.program.model.listing.*;
import ghidra.program.model.pcode.*;
import ghidra.program.model.symbol.SourceType;
import ghidra.program.model.symbol.Symbol;

public class DecompilerHighSymbolTest extends AbstractDecompilerTest {
	@Override
	protected String getProgramName() {
		return "Winmine__XP.exe.gzf";
	}

	private void renameGlobalVariable(HighSymbol highSymbol, ClangToken tokenAtCursor,
			String newName) {
		Address addr = highSymbol.getStorage().getMinAddress();
		applyCmd(program,
			new RenameLabelCmd(addr, highSymbol.getName(), newName, SourceType.USER_DEFINED));
		waitForDecompiler();
	}

	private void deleteFunction(String address) {
		Address addr = program.getAddressFactory().getAddress(address);
		applyCmd(program, new DeleteFunctionCmd(addr));
	}

	private void createFunction(String address) {
		Address addr = program.getAddressFactory().getAddress(address);
		applyCmd(program, new CreateFunctionCmd(addr));
	}

	private void turnOffAnalysis() {
		modifyProgram(p -> {
			Options options = p.getOptions(Program.ANALYSIS_PROPERTIES);
			options.setBoolean("Decompiler Parameter ID", false);
			options.setBoolean("Stack", false);
		});
	}

	private void renameVariable(HighSymbol highSymbol, ClangToken tokenAtCursor, String newName) {
		RenameVariableTask rename = new RenameVariableTask(provider.getTool(),
			highSymbol.getProgram(), provider, tokenAtCursor, highSymbol, SourceType.USER_DEFINED);
		assertTrue(rename.isValid(newName));
		modifyProgram(p -> {
			rename.commit();
		});
		waitForDecompiler();
	}

	private void isolateVariable(HighSymbol highSymbol, ClangToken tokenAtCursor, String newName) {
		IsolateVariableTask isolate = new IsolateVariableTask(provider.getTool(), program, provider,
			tokenAtCursor, highSymbol, SourceType.USER_DEFINED);
		assertTrue(isolate.isValid(newName));
		modifyProgram(p -> {
			isolate.commit();
		});
		waitForDecompiler();
	}

	private void applyEquate(String equateName, Address addr, long equateValue) {
		applyCmd(program, new SetEquateCmd(equateName, addr, 0, equateValue));
	}

	private void renameExisting(HighSymbol highSymbol, ClangToken tokenAtCursor, String newName) {
		SymbolEntry oldEntry = highSymbol.getFirstWholeMap();
		long oldId = highSymbol.getId();
		if (highSymbol.isGlobal()) {
			renameGlobalVariable(highSymbol, tokenAtCursor, newName);
		}
		else {
			renameVariable(highSymbol, tokenAtCursor, newName);
		}
		Symbol symbol = program.getSymbolTable().getSymbol(oldId);
		assertEquals(symbol.getName(), newName);
		HighFunction highFunction = getHighFunction();
		HighSymbol newHighSymbol = highFunction.getLocalSymbolMap().getSymbol(oldId);
		if (newHighSymbol == null) {
			newHighSymbol = highFunction.getGlobalSymbolMap().getSymbol(oldId);
		}
		assertNotNull(newHighSymbol);
		SymbolEntry newEntry = newHighSymbol.getFirstWholeMap();
		assertEquals(oldEntry.getStorage(), newEntry.getStorage());
	}

	@Test
	public void testHighSymbol_globalRename() {

		decompile("1001b49");

		ClangTextField line = getLineStarting("DAT_010056a0");
		FieldLocation loc = loc(line.getLineNumber(), 5);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighGlobal);
		HighSymbol highSymbol = variable.getSymbol();
		assertTrue(highSymbol instanceof HighCodeSymbol);
		HighCodeSymbol highCode = (HighCodeSymbol) highSymbol;
		CodeSymbol codeSymbol = highCode.getCodeSymbol();
		assertNull(codeSymbol);	// A DAT_ should not have a permanent CodeSymbol
		Data data = highCode.getData();
		assertNotNull(data);
		assertEquals(0x10056a0L, data.getAddress().getOffset());
		assertEquals(2, data.getBaseDataType().getLength());

		renameGlobalVariable(highSymbol, token, "newGlobal");
		waitForDecompiler();
		line = getLineStarting("newGlobal");
		loc = loc(line.getLineNumber(), 5);
		token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		variable = token.getHighVariable();
		assertTrue(variable instanceof HighGlobal);
		highSymbol = variable.getSymbol();
		assertTrue(highSymbol instanceof HighCodeSymbol);
		highCode = (HighCodeSymbol) highSymbol;
		assertTrue(highCode.isGlobal());
		assertTrue(highCode.isNameLocked());
		assertTrue(highCode.isTypeLocked());
		codeSymbol = highCode.getCodeSymbol();
		assertNotNull(codeSymbol);
		assertEquals(codeSymbol.getID(), highCode.getId());
		renameExisting(highSymbol, token, "nameAgain");
	}

	@Test
	public void testHighSymbol_localStackDynamic() {
		decompile("10015a6");
		ClangTextField line = getLineContaining(" = 0xc;");
		FieldLocation loc = loc(line.getLineNumber(), 2);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighLocal);
		HighSymbol highSymbol = variable.getSymbol();
		SymbolEntry entry = highSymbol.getFirstWholeMap();
		assertTrue(entry instanceof MappedEntry);		// Comes back initially as untied stack location
		int stackCount = 0;
		int regCount = 0;
		int numInst = variable.getInstances().length;
		for (Varnode var : variable.getInstances()) {
			if (var.isRegister() || var.isAddrTied()) {
				regCount += 1;
			}
			else if (var.getAddress().isStackAddress()) {
				stackCount += 1;
			}
		}
		assertTrue(stackCount > 0);		// Verify speculative merge
		assertTrue(regCount > 0);
		renameVariable(highSymbol, token, "newLocal");
		line = getLineStarting("newLocal");
		loc = loc(line.getLineNumber(), 5);
		token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		variable = token.getHighVariable();
		assertTrue(variable instanceof HighLocal);
		highSymbol = variable.getSymbol();
		entry = highSymbol.getFirstWholeMap();
		assertTrue(entry instanceof DynamicEntry);	// After rename comes back as HASH
		assertTrue(entry.getPCAdress().getOffset() == 0x10016a3);
		assertTrue(highSymbol.isNameLocked());
		assertFalse(highSymbol.isTypeLocked());
		assertEquals(numInst, variable.getInstances().length);
		assertEquals(0xfffffffffffffff0L, variable.getRepresentative().getAddress().getOffset());
		renameExisting(highSymbol, token, "nameAgain");
	}

	@Test
	public void testHighSymbol_localArray() {
		decompile("10016ba");
		ClangTextField line = getLineStarting("wsprintfW");
		FieldLocation loc = loc(line.getLineNumber(), 14);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		assertNull(token.getHighVariable());		// No HighVariable associated with the token
		PcodeOp op = ((ClangVariableToken) token).getPcodeOp();
		AddressFactory addrFactory = provider.getProgram().getAddressFactory();
		Address addr = HighFunctionDBUtil.getSpacebaseReferenceAddress(addrFactory, op);
		HighFunction highFunction = getHighFunction();
		LocalSymbolMap lsym = highFunction.getLocalSymbolMap();
		HighSymbol highSymbol = lsym.findLocal(addr, null);
		assertEquals("local_44", highSymbol.getName());
		renameVariable(highSymbol, token, "newArray");
		line = getLineStarting("wsprintfW");
		token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		assertEquals("newArray", token.getText());		// Name has changed
		highFunction = getHighFunction();
		lsym = highFunction.getLocalSymbolMap();
		highSymbol = lsym.findLocal(addr, null);
		assertEquals("newArray", highSymbol.getName());
		assertTrue(highSymbol.isNameLocked());
		assertFalse(highSymbol.isTypeLocked());
		SymbolEntry entry = highSymbol.getFirstWholeMap();
		assertTrue(entry instanceof MappedEntry);
		assertEquals(entry.getStorage().getMinAddress(), addr);
		assertEquals(64, entry.getSize());
		renameExisting(highSymbol, token, "nameAgain");
	}

	@Test
	public void testHighSymbol_localRegister() {
		decompile("1002607");
		ClangTextField line = getLineStarting("iVar");
		FieldLocation loc = loc(line.getLineNumber(), 1);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighLocal);
		HighSymbol highSymbol = variable.getSymbol();
		SymbolEntry entry = highSymbol.getFirstWholeMap();
		Address addr = entry.getStorage().getMinAddress();
		assertTrue(entry instanceof MappedEntry);		// Comes back initially as untied stack location
		assertEquals("register", addr.getAddressSpace().getName());
		renameVariable(highSymbol, token, "newReg");
		line = getLineContaining("newReg < 0x40");
		assertNotNull(line);
		loc = loc(line.getLineNumber(), 11);
		token = line.getToken(loc);
		HighFunction highFunction = getHighFunction();
		highSymbol = highFunction.getLocalSymbolMap().findLocal(addr, entry.getPCAdress());
		assertNotNull(highSymbol);
		assertEquals("newReg", highSymbol.getName());
		assertTrue(highSymbol.isNameLocked());
		assertFalse(highSymbol.isTypeLocked());
		renameExisting(highSymbol, token, "nameAgain");
	}

	@Test
	public void testHighSymbol_parameter() {
		decompile("1002d7a");
		ClangTextField line = getLineContaining("strlen");
		FieldLocation loc = loc(line.getLineNumber(), 20);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighParam);
		HighSymbol highSymbol = variable.getSymbol();
		assertEquals("param_2", highSymbol.getName());
		assertTrue(highSymbol.isParameter());
		assertEquals(1, highSymbol.getCategoryIndex());
		SymbolEntry entry = highSymbol.getFirstWholeMap();
		Address addr = entry.getStorage().getMinAddress();
		assertEquals(8L, addr.getOffset());
		renameExisting(highSymbol, token, "paramAgain");
	}

	@Test
	public void testHighSymbol_multipleUsePoints() {
		decompile("1001915");
		ClangTextField line = getLineContaining("0x4e");
		FieldLocation loc = loc(line.getLineNumber(), 2);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighLocal);
		HighSymbol highSymbol = variable.getSymbol();
		SymbolEntry entry = highSymbol.getFirstWholeMap();
		assertTrue(entry instanceof MappedEntry);
		Address usepoint = token.getVarnode().getPCAddress();
		renameVariable(highSymbol, token, "newLocal");
		line = getLineContaining("0x4e");
		token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		assertEquals("newLocal", token.getText());		// Name has changed
		variable = token.getHighVariable();
		highSymbol = variable.getSymbol();
		entry = highSymbol.getFirstWholeMap();
		assertEquals(usepoint, entry.getPCAdress());		// Make sure the same usepoint comes back
	}

	@Test
	public void testHighSymbol_freeParameter() {
		deleteFunction("10015a6");
		turnOffAnalysis();
		createFunction("10015a6");
		decompile("10015a6");

		ClangTextField line = getLineContaining("param_4 +");
		FieldLocation loc = loc(line.getLineNumber(), 23);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);

		HighSymbol highSymbol = token.getHighVariable().getSymbol();
		renameVariable(highSymbol, token, "newParam");
		line = getLineContaining("newParam +");
		token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);

		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighParam);
		assertEquals(3, ((HighParam) variable).getSlot());

		highSymbol = variable.getSymbol();
		assertTrue(highSymbol.isNameLocked());
		assertFalse(highSymbol.isTypeLocked());

		Function function = highSymbol.getHighFunction().getFunction();
		Parameter[] parameters = function.getParameters();
		assertEquals(4, parameters.length);
		for (int i = 0; i < 4; ++i) {
			DataType dt = parameters[i].getDataType();
			assertTrue(Undefined.isUndefined(dt));
			assertEquals(4, dt.getLength());
		}
		assertEquals("newParam", parameters[3].getName());
	}

	@Test
	public void testHighSymbol_isolate() {
		decompile("1002a22");
		ClangTextField line = getLineContaining(" >> 1");
		FieldLocation loc = loc(line.getLineNumber(), 2);
		ClangToken token = line.getToken(loc);
		assertTrue(token instanceof ClangVariableToken);
		HighVariable variable = token.getHighVariable();
		Varnode[] instances = variable.getInstances();
		short maxMerge = 0;
		for (Varnode vn : instances) {
			if (vn.getMergeGroup() > maxMerge) {
				maxMerge = vn.getMergeGroup();
			}
		}
		assertEquals(1, maxMerge);		// Make sure there are 2 merge groups
		String name = token.getText();
		isolateVariable(variable.getSymbol(), token, name);
		line = getLineContaining(" >> 1");
		token = line.getToken(loc);
		variable = token.getHighVariable();
		assertEquals(1, variable.getInstances().length);
		HighSymbol highSymbol = variable.getSymbol();
		assertEquals(highSymbol.getName(), name);
		assertTrue(highSymbol.isNameLocked());
		assertTrue(highSymbol.isTypeLocked());
	}

	@Test
	public void testHighSymbol_convert() {
		Address subAddr = addr(0x10015ac);
		String equateName = "00000000000000000000000001010011b";
		int equateValue = 0x53;
		applyEquate(equateName, subAddr, equateValue);
		decompile("10015ac");
		ClangTextField line = getLineContaining("if (param");
		FieldLocation loc = loc(line.getLineNumber(), 20);
		ClangToken token = line.getToken(loc);
		assertTrue(token.getText().equals("0b01010011"));
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighConstant);
		HighSymbol highSymbol = variable.getSymbol();
		assertTrue(highSymbol instanceof EquateSymbol);
		EquateSymbol eqSymbol = (EquateSymbol) highSymbol;
		assertEquals(EquateSymbol.FORMAT_BIN, eqSymbol.getConvert());
		assertEquals(equateValue, eqSymbol.getValue());
	}

	@Test
	public void testHighSymbol_dualequates() {
		// Two equates on the same value at different locations
		// One a convert, and one a label
		Address convAddr = addr(0x100165a);
		String convName = "141";
		int convValue = 0x8d;
		applyEquate(convName, convAddr, convValue);
		Address eqAddr = addr(0x10015f1);
		String eqName = "BIGEQUATE";
		int eqValue = 0x8d;
		applyEquate(eqName, eqAddr, eqValue);
		decompile("10015ac");
		ClangTextField line = getLineContaining(",DAT_010056a8");
		FieldLocation loc = loc(line.getLineNumber(), 23);
		ClangToken token = line.getToken(loc);
		assertTrue(token.getText().equals("141"));
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighConstant);
		HighSymbol highSymbol = variable.getSymbol();
		assertTrue(highSymbol instanceof EquateSymbol);
		EquateSymbol eqSymbol = (EquateSymbol) highSymbol;
		assertEquals(EquateSymbol.FORMAT_DEC, eqSymbol.getConvert());
		assertEquals(convValue, eqSymbol.getValue());

		line = getLineContaining("DAT_010056a8 = ");
		loc = loc(line.getLineNumber(), 39);
		token = line.getToken(loc);
		assertTrue(token.getText().equals(eqName));
		variable = token.getHighVariable();
		assertTrue(variable instanceof HighConstant);
		highSymbol = variable.getSymbol();
		assertTrue(highSymbol instanceof EquateSymbol);
		eqSymbol = (EquateSymbol) highSymbol;
		assertEquals(0, eqSymbol.getConvert());
		assertEquals(eqValue, eqSymbol.getValue());
	}

	@Test
	public void testHighSymbol_resolveStorage() {
		decompile("1002825");
		ClangTextField line = getLineContaining("DAT_0100579c");
		FieldLocation loc = loc(line.getLineNumber(), 3);
		ClangToken token = line.getToken(loc);
		HighVariable variable = token.getHighVariable();
		assertTrue(variable instanceof HighLocal);
		HighSymbol highSymbol = variable.getSymbol();
		SymbolEntry entry = highSymbol.getFirstWholeMap();
		if (entry instanceof DynamicEntry) {
			assertTrue(((DynamicEntry) entry).getHash() == 0);	// Initially hash is not calculuated
		}
		else {
			assertTrue(entry instanceof MappedEntry);
			VariableStorage storage = ((MappedEntry) entry).getStorage();
			assertTrue(storage.getMinAddress().isUniqueAddress());
		}
		renameVariable(highSymbol, token, "newDynamic");
		line = getLineContaining("DAT_0100579c");
		loc = loc(line.getLineNumber(), 3);
		token = line.getToken(loc);
		highSymbol = token.getHighVariable().getSymbol();
		entry = highSymbol.getFirstWholeMap();
		assertTrue(entry instanceof DynamicEntry);
		assertTrue(((DynamicEntry) entry).getHash() != 0);	// With rename hash is calculated
	}
}
