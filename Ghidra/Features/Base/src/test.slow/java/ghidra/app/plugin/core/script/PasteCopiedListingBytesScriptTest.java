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
package ghidra.app.plugin.core.script;

import static org.junit.Assert.*;

import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;

import org.junit.*;

import docking.dnd.GClipboard;
import ghidra.framework.Application;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.test.*;

/**
 * Tests the {@code PasteCopiedListingBytesScript}, which grabs the copy buffer, and
 * attempts to parse out address/bytes from either listing format or hexdump format.
 */
public class PasteCopiedListingBytesScriptTest extends AbstractGhidraHeadedIntegrationTest {

	private TestEnv env;
	private File script;

	private Program program;

	@Before
	public void setUp() throws Exception {

		program = buildProgram();

		env = new TestEnv();
		env.launchDefaultTool(program);

		String scriptPath = "ghidra_scripts/PasteCopiedListingBytesScript.java";
		script = Application.getModuleFile("Base", scriptPath).getFile(true);
	}
	
	@After
	public void tearDown() {
		env.dispose();
	}

	private Program buildProgram() throws Exception {
		ToyProgramBuilder builder = new ToyProgramBuilder("Test", true, this);

		return builder.getProgram();
	}

	@Test
	public void testSetGetClipBoard() throws Exception {

		setClipBoardContents("foo");
		
		Clipboard systemClipboard = GClipboard.getSystemClipboard();
		Transferable contents = systemClipboard.getContents(this);	
		assertEquals("foo", contents.getTransferData(DataFlavor.stringFlavor));
		
	}
	
	@Test
	public void testNoneValid() throws Exception {
		
		setClipBoardContents("Hex dump of section '.text':\n" + 
			" NOTE: This section has relocations against it, but these have NOT been applied to this dump.\n");
		
		ScriptTaskListener listener = env.runScript(script);

		waitForScriptCompletion(listener, 20000);

		// test that memory blocks created
		MemoryBlock[] blocks = program.getMemory().getBlocks();
		assertEquals(0,blocks.length);
	}
	
	@Test
	public void testNoText() throws Exception {
		
		// clear clipboard
		Clipboard systemClipboard = GClipboard.getSystemClipboard();
		systemClipboard.setContents(new Transferable() {
			
			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return flavor.equals(DataFlavor.stringFlavor);
			}
			
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				DataFlavor[] df = new DataFlavor[] { DataFlavor.stringFlavor };
				return df;
			}
			
			@Override
			public Object getTransferData(DataFlavor flavor)
					throws UnsupportedFlavorException, IOException {
				return null;
			}
		}, new ClipboardOwner() {
			
			@Override
			public void lostOwnership(Clipboard clipboard, Transferable contents) {
			}
		});
		
		ScriptTaskListener listener = env.runScript(script);

		waitForScriptCompletion(listener, 20000);

		// test that memory blocks created
		MemoryBlock[] blocks = program.getMemory().getBlocks();
		assertEquals(0,blocks.length);
	}
	
	@Test
	public void testPasteListing() throws Exception {
		
		setClipBoardContents(
			"               00010004 f0 07 a0 40     060           lduw       [fp+local_res40],i0\n" + 
			"               00010008 81 c7 e0 0c     060           jmpl       i7+0xc\n" + 
			"               0001000c 81 e8 00 00                   _restore\n" +
			"          LAB_00010024                                                                                                                                           XREF[1]:      00010018  \n" + 
			"               00010024 f0 07 bf f8                   lduw       [fp+-0x8],i0\n" + 
			"               00010028 40 00 03 f6                   call       EXTERNAL:<EXTERNAL>::r                                                                                                                          undefined r()\n" + 
			"               0001002c f0 27 bf                     _stw       i0,[fp+-0xc]\n" + 
			"");
		
		ScriptTaskListener listener = env.runScript(script);

		waitForScriptCompletion(listener, 20000);

		// test that memory blocks created
		MemoryBlock[] blocks = program.getMemory().getBlocks();
		assertEquals(2,blocks.length);
		MemoryBlock block = blocks[0];
	
		assertEquals(0x10004L, block.getStart().getOffset());
		assertEquals(0x1000fL, block.getEnd().getOffset());
		Address addr = block.getStart();
		assertEquals((byte)0xf0,block.getByte(addr));
		assertEquals((byte)0x81,block.getByte(addr.getAddress("0x1000c")));
		assertEquals((byte)0x00,block.getByte(addr.getAddress("0x1000f")));
		
		block = blocks[1];
		assertEquals(0x10024L, block.getStart().getOffset());
		assertEquals(0x1002eL, block.getEnd().getOffset());
		addr = block.getStart();
		assertEquals((byte)0xf0,block.getByte(addr.getAddress("0x10024")));
		assertEquals((byte)0x03,block.getByte(addr.getAddress("0x1002a")));
		assertEquals((byte)0xbf,block.getByte(addr.getAddress("0x1002e")));

	}
	
	@Test
	public void testPasteListingMultiLine() throws Exception {
		
		setClipBoardContents(
			"                             //\n" + 
			"                             **************************************************************\n" + 
			"                             *                          FUNCTION                          *\n" + 
			"                             **************************************************************\n" + 
			"                             F __stdcall f(F * __return_storage_ptr__)\n" + 
			"             F                      o0:4 (ptr)     <RETURN>\n" + 
			"             F *                    Stack[0x40]:   __return_storage_ptr__                  XREF[1]:     00010004(R)  \n" + 
			"             undefined4             Stack[0x40]:4  local_res40                             XREF[1]:     00010004(R)  \n" + 
			"          f                                                                                                                                                      XREF[3]:      Entry Point(*), 00010018(c), \n" + 
			"                                                                                                                                                                                _elfSectionHeaders::0000005c(*)  \n" + 
			"               00010000 9d e3          0           save       sp,-0x60,sp\n" + 
			"                        bf a0\n" + 
			"               00010004 f0 07        060           lduw       [fp+local_res40],i0\n" + 
			"                        a0 40\n" + 
			"               00010008 81 c7        060           jmpl       i7+0xc\n" + 
			"                        e0 0c\n" + 
			"               0001000c 81 e8                      _restore\n" + 
			"                        00 00\n" +
            "               00010014 b0 07                      add        fp,-0x8,i0\n" +
            "                        bf f8\n" +
			" ");
		
		ScriptTaskListener listener = env.runScript(script);

		waitForScriptCompletion(listener, 20000);

		// test that memory blocks created
		MemoryBlock[] blocks = program.getMemory().getBlocks();
		assertEquals(2,blocks.length);
		MemoryBlock block = blocks[0];
	
		assertEquals(0x10000L, block.getStart().getOffset());
		assertEquals(0x1000fL, block.getEnd().getOffset());
		Address addr = block.getStart();
		assertEquals((byte)0x9d,block.getByte(addr));
		assertEquals((byte)0x81,block.getByte(addr.getAddress("0x1000c")));
		assertEquals((byte)0x00,block.getByte(addr.getAddress("0x1000f")));
		
		block = blocks[1];
		assertEquals(0x10014L, block.getStart().getOffset());
		assertEquals(0x10017L, block.getEnd().getOffset());
		addr = block.getStart();
		assertEquals((byte)0xb0,block.getByte(addr));
		assertEquals((byte)0xbf,block.getByte(addr.getAddress("0x10016")));
		assertEquals((byte)0xf8,block.getByte(addr.getAddress("0x10017")));

	}
	
	@Test
	public void testPasteHexDump1Block() throws Exception {
		
		setClipBoardContents("Hex dump of section '.text':\n" + 
			" NOTE: This section has relocations against it, but these have NOT been applied to this dump.\n" + 
			"  0x00000000 80b487b0 00aff860 c7e90023 3b683b61 .......`...#;h;a\n" + 
			"  0x00000010 b7f92030 7b61fb68 1a4607f1 100393e8 .. 0{a.h.F......\n" + 
			"  0x00000020 030082e8 0300f868 1c37bd46 5df8047b .......h.7.F]..{\n" + 
			"  0x00000030 704780b4 83b000af 87ed000b 4ff0ff33 pG..........O..3\n" + 
			"  0x00000040 18460c37 bd465df8 047b7047 80b485b0 .F.7.F]..{pG....");
		
		ScriptTaskListener listener = env.runScript(script);

		waitForScriptCompletion(listener, 20000);

		// test that memory blocks created
		MemoryBlock[] blocks = program.getMemory().getBlocks();
		assertEquals(1,blocks.length);
		MemoryBlock block = blocks[0];
	
		assertEquals(0x0000L, block.getStart().getOffset());
		assertEquals(0x004fL, block.getEnd().getOffset());
		Address addr = block.getStart();
		assertEquals((byte)0x80,block.getByte(addr));
		assertEquals((byte)0x03,block.getByte(addr.getAddress("0x0024")));
		assertEquals((byte)0xb0,block.getByte(addr.getAddress("0x004f")));
	}
	
	@Test
	public void testPasteHexDump3Blocks() throws Exception {
		
		setClipBoardContents("Hex dump of section '.text':\n" + 
			" NOTE: This section has relocations against it, but these have NOT been applied to this dump.\n" + 
			"  0x00000000 80b487b0 00aff860 c7e90023 3b683b61 .......`...#;h;a\n" + 
			"  0x00000010 b7f92030 7b61fb68 1a4607f1          .. 0{a.h.F......\n" + 
			"  0x00000020 030082e8 0300f868 1c37bd46 5df8047b .......h.7.F]..{\n" + 
			"  0x00000030 704780b4 83b000af 87ed              pG..........O..3\n" + 
			"  0x00000040 18460c37 bd465df8 047b7047 80b485b0 .F.7.F]..{pG....");
		
		ScriptTaskListener listener = env.runScript(script);

		waitForScriptCompletion(listener, 20000);

		// test that memory blocks created
		MemoryBlock[] blocks = program.getMemory().getBlocks();
		assertEquals(3,blocks.length);
		MemoryBlock block = blocks[0];
	
		assertEquals(28, block.getSize());
		assertEquals(0x00000L, block.getStart().getOffset());

		Address addr = block.getStart();
		assertEquals((byte)0x80,block.getByte(addr.getAddress("0x00000")));
		assertEquals((byte)0xb7,block.getByte(addr.getAddress("0x00010")));
		assertEquals((byte)0xf1,block.getByte(addr.getAddress("0x0001b")));
		
		block = blocks[1];
		
		assertEquals(26, block.getSize());
		assertEquals(0x00020L, block.getStart().getOffset());

		addr = block.getStart();
		assertEquals((byte)0x03,block.getByte(addr.getAddress("0x00020")));
		assertEquals((byte)0x70,block.getByte(addr.getAddress("0x00030")));
		assertEquals((byte)0xed,block.getByte(addr.getAddress("0x00039")));
		
		block = blocks[2];
		
		assertEquals(16, block.getSize());
		assertEquals(0x00040L, block.getStart().getOffset());

		addr = block.getStart();
		assertEquals((byte)0x18,block.getByte(addr.getAddress("0x00040")));
		assertEquals((byte)0x04,block.getByte(addr.getAddress("0x00048")));
		assertEquals((byte)0xb0,block.getByte(addr.getAddress("0x0004f")));
	}

	private Clipboard setClipBoardContents(String str) {
		// put stuff in copy buffer
		Clipboard systemClipboard = GClipboard.getSystemClipboard();
		systemClipboard.setContents(new Transferable() {
			
			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return flavor.equals(DataFlavor.stringFlavor);
			}
			
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				DataFlavor[] df = new DataFlavor[] { DataFlavor.stringFlavor };
				return df;
			}
			
			@Override
			public Object getTransferData(DataFlavor flavor)
					throws UnsupportedFlavorException, IOException {
				if (!flavor.equals(DataFlavor.stringFlavor)) {
					return null;
				}
				return str;
			}
		}, new ClipboardOwner() {
			
			@Override
			public void lostOwnership(Clipboard clipboard, Transferable contents) {
			}
		});
		return systemClipboard;
	}
}
