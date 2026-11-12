// Original, deterministic programs for comparison, VT, BSim and staged merge reviews.
// Headless post-script; arguments: output directory. Binary names select the variant.
import java.nio.file.*;
import java.util.*;

import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.util.GhidraProgramUtilities;

public class CreateSpecialistPrograms extends GhidraScript {
    @Override
    protected void run() throws Exception {
        String variant = currentProgram.getName().replace(".bin", "");
        boolean destination = variant.equals("destination");
        boolean latest = variant.equals("merge-latest");
        boolean privateCopy = variant.equals("merge-private");
        currentProgram.getMemory().getBlock(toAddr(0x401000)).setExecute(true);
        Namespace scope = currentProgram.getSymbolTable().createNameSpace(null,
            "specialist_fixture_with_a_deliberately_long_namespace", SourceType.USER_DEFINED);
        for (long offset : new long[] {0, 0x40, 0x80, 0xa0, destination ? 0x140 : 0x100}) {
            disassemble(toAddr(0x401000 + offset));
            String name = switch ((int) offset) {
                case 0 -> destination ? "destination_classify" : latest ? "latest_classify"
                    : privateCopy ? "private_classify" : "source_classify";
                case 0x40 -> "accumulate_location_history";
                case 0x80 -> "duplicate_recent_location_a";
                case 0xa0 -> "duplicate_recent_location_b";
                default -> destination ? "destination_only" : "source_only";
            };
            Function function = createFunction(toAddr(0x401000 + offset), name);
            if (function == null) throw new AssertionError("Missing function " + name);
            function.setParentNamespace(scope);
            function.setCallingConvention("__fastcall");
            function.setReturnType(IntegerDataType.dataType, SourceType.USER_DEFINED);
            if (offset == 0 || offset == 0x40) {
                function.replaceParameters(Function.FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,
                    true, SourceType.USER_DEFINED, new ParameterImpl(destination ? "candidate_id" :
                    "location_id", destination ? UnsignedIntegerDataType.dataType :
                    IntegerDataType.dataType, currentProgram));
            }
        }
        StructureDataType record = new StructureDataType("LocationReviewRecord", 0);
        record.add(IntegerDataType.dataType, "location_id", "Recorded identifier");
        record.add(destination ? QWordDataType.dataType : DWordDataType.dataType,
            "history_offset", "Controlled type/size difference");
        createData(toAddr(0x401200), record);
        createLabel(toAddr(0x401200), "location_review_record", true);
        createAsciiString(toAddr(0x401280));
        setPlateComment(toAddr(0x401000), "Specialist fixture: " + variant +
            ". Synthetic raw x86-64; not a launchable Windows executable.");
        setEOLComment(toAddr(0x401004), latest ? "Latest branch changed this comment" :
            privateCopy ? "Private branch changed this comment" : "Common ancestor comment");
        GhidraProgramUtilities.markProgramAnalyzed(currentProgram);
        if (currentProgram.getFunctionManager().getFunctionCount() != 5) {
            throw new AssertionError("Expected exactly five fixture functions");
        }
        Path report = Paths.get(getScriptArgs()[0], variant + ".program.tsv");
        List<String> rows = new ArrayList<>();
        rows.add("program\tfunction\tentry\tbytes");
        for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
            rows.add(variant + "\t" + f.getName(true) + "\t" + f.getEntryPoint() + "\t" +
                f.getBody().getNumAddresses());
        }
        Files.write(report, rows);
    }
}
