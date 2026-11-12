// Cross-process persistence checks. Run after all preparation, -process source.bin.
import java.nio.file.*;
import java.util.*;

import ghidra.app.script.GhidraScript;
import ghidra.feature.vt.api.main.*;
import ghidra.features.bsim.query.*;
import ghidra.features.bsim.query.protocol.*;
import ghidra.framework.model.*;
import ghidra.program.model.listing.Program;
import ghidra.trace.model.Trace;

public class VerifySpecialistFixtures extends GhidraScript {
    @Override
    protected void run() throws Exception {
        Path output = Paths.get(getScriptArgs()[0]);
        DomainFolder programs = currentProgram.getDomainFile().getParent();
        List<String> report = new ArrayList<>();
        for (String name : List.of("source", "destination", "merge-base", "merge-latest", "merge-private")) {
            Program program = (Program) programs.getFile(name + ".bin").getDomainObject(this, false, false, monitor);
            try {
                if (program.getFunctionManager().getFunctionCount() != 5) throw new AssertionError(name);
                var space = program.getAddressFactory().getDefaultAddressSpace();
                int immediate = program.getMemory().getByte(space.getAddress(0x401006)) & 255;
                int expected = switch (name) {
                    case "destination" -> 43;
                    case "merge-latest" -> 44;
                    case "merge-private" -> 45;
                    default -> 42;
                };
                if (immediate != expected) throw new AssertionError("Wrong variant byte: " + name);
                int recordSize = program.getListing().getDataAt(space.getAddress(0x401200)).getLength();
                if (recordSize != (name.equals("destination") ? 12 : 8)) throw new AssertionError("Wrong type: " + name);
                report.add(name + ": five functions; compare immediate=" + immediate + "; record bytes=" + recordSize);
            }
            finally { program.release(this); }
        }
        DomainObject object = programs.getFile("UI Specialist VT").getDomainObject(this, false, false, monitor);
        try {
            VTSession session = (VTSession) object;
            Map<String, Integer> states = new TreeMap<>();
            for (VTMatch match : session.getManualMatchSet().getMatches()) {
                states.merge(match.getAssociation().getStatus().name(), 1, Integer::sum);
            }
            if (!states.equals(Map.of("ACCEPTED", 1, "AVAILABLE", 2, "REJECTED", 1))) throw new AssertionError(states);
            report.add("Reopened VT session: " + states);
        }
        finally { object.release(this); }
        object = state.getProject().getProjectData().getRootFolder().getFile("UI Debugger Trace")
            .getDomainObject(this, false, false, monitor);
        try {
            Trace trace = (Trace) object;
            if (trace.getTimeManager().getAllSnapshots().size() != 21 ||
                    trace.getThreadManager().getAllThreads().size() != 2) throw new AssertionError("Incomplete trace");
            report.add("Reopened saved debugger trace: 21 snapshots; two threads");
        }
        finally { object.release(this); }
        BSimServerInfo server = new BSimServerInfo(output.resolve("ui-specialist-bsim").toString());
        try (FunctionDatabase db = BSimClientFactory.buildClient(server, false)) {
            if (!db.initialize()) throw new AssertionError(db.getLastError().message);
            ResponseExe count = new QueryExeCount().execute(db);
            if (count == null || count.recordCount != 2) throw new AssertionError("Incomplete BSim database");
            GenSignatures signatures = new GenSignatures(true);
            try {
                signatures.setVectorFactory(db.getLSHVectorFactory());
                signatures.openProgram(currentProgram, null, null, null, null, null);
                signatures.scanFunctions(currentProgram.getFunctionManager().getFunctions(true), 5, monitor);
                QueryNearest query = new QueryNearest();
                query.manage = signatures.getDescriptionManager();
                query.thresh = 0.7;
                query.signifthresh = 0.0;
                ResponseNearest response = query.execute(db);
                if (response == null) throw new AssertionError(db.getLastError().message);
                int crossProgram = 0;
                for (SimilarityResult result : response.result) {
                    for (SimilarityNote note : result) {
                        if (!note.getFunctionDescription().getExecutableRecord().getMd5().equals(currentProgram.getExecutableMD5())) crossProgram++;
                    }
                }
                if (crossProgram < 3) throw new AssertionError("Expected cross-program BSim matches: " + crossProgram);
                report.add("Reopened BSim database: two executables; source query hits in destination=" + crossProgram);
            }
            finally { signatures.dispose(); }
        }
        Files.write(output.resolve("verification.txt"), report);
        Files.writeString(output.resolve("verified.ready"), "Cross-process specialist fixture checks passed\n");
    }
}
