// Creates original deterministic content for UI reference captures; no external binary required.
// Run as a headless post-script after importing the bytes from prepare-fixture.ps1 as x86-64.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.util.GhidraProgramUtilities;

public class CreateUiFixture extends GhidraScript {
    @Override
    protected void run() throws Exception {
        currentProgram.getMemory().getBlock(toAddr(0x401000)).setExecute(true);
        disassemble(toAddr(0x401000));
        Function function = createFunction(toAddr(0x401000), "classify_address_for_preview");
        if (function == null) throw new AssertionError("Fixture function creation failed");
        Namespace root = currentProgram.getSymbolTable().createNameSpace(null, "ui_fidelity", SourceType.USER_DEFINED);
        Namespace scope = currentProgram.getSymbolTable().createNameSpace(root,
            "navigation_with_a_deliberately_long_namespace", SourceType.USER_DEFINED);
        function.setParentNamespace(scope);
        function.setCallingConvention("__fastcall");
        function.setReturnType(IntegerDataType.dataType, SourceType.USER_DEFINED);
        function.replaceParameters(Function.FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS, true,
            SourceType.USER_DEFINED, new ParameterImpl("location_id", IntegerDataType.dataType, currentProgram));
        setPlateComment(toAddr(0x401000), "UI fidelity fixture: two branches, shared exit, long namespace.\nOriginal x86-64 bytes; no external executable or service.");
        disassemble(toAddr(0x401040));
        createFunction(toAddr(0x401040), "resolve_recent_location");
        createLabel(toAddr(0x401080), "navigation_history_state_with_a_long_label", true);
        createAsciiString(toAddr(0x401080));
        for (int i = 0; i < 48; i++) {
            createLabel(toAddr(0x4010c0 + i), String.format("recent_location_%02d_with_a_long_symbol_label", i), true);
        }
        GhidraProgramUtilities.markProgramAnalyzed(currentProgram);
        println("UI fixture ready: " + function.getName(true) + " / " + function.getBody().getNumAddresses() + " code bytes");
    }
}
