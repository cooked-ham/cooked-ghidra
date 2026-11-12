// Creates saved VT and local BSim fixtures from already-saved specialist programs.
// Run with -process source.bin -noanalysis; argument: fixture output directory.
import java.nio.file.*;
import java.util.*;

import ghidra.app.script.GhidraScript;
import ghidra.feature.vt.api.db.VTSessionDB;
import ghidra.feature.vt.api.main.*;
import ghidra.features.bsim.query.*;
import ghidra.features.bsim.query.description.DatabaseInformation;
import ghidra.features.bsim.query.protocol.*;
import ghidra.framework.model.*;
import ghidra.program.model.listing.Program;

public class CreateSpecialistSessions extends GhidraScript {
    @Override
    protected void run() throws Exception {
        Path output = Paths.get(getScriptArgs()[0]);
        DomainFolder root = currentProgram.getDomainFile().getParent();
        Program destination = (Program) root.getFile("destination.bin").getDomainObject(
            this, false, false, monitor);
        try {
            createVersionTracking(root, currentProgram, destination, output);
            createBSim(currentProgram, destination, output);
        }
        finally {
            destination.release(this);
        }
        Files.writeString(output.resolve("specialists.ready"), "VT session and BSim query verified\n");
    }

    private void createVersionTracking(DomainFolder root, Program source, Program destination,
            Path output) throws Exception {
        VTSessionDB session = new VTSessionDB("UI Specialist VT", source, destination, this);
        try {
            int tx = session.startTransaction("Deterministic accepted/rejected/ambiguous matches");
            boolean commit = false;
            try {
                VTMatchSet matches = session.getManualMatchSet();
                match(matches, source, destination, 0, 0).getAssociation().setAccepted();
                match(matches, source, destination, 0x40, 0x40).getAssociation().setRejected();
                match(matches, source, destination, 0x80, 0x80);
                match(matches, source, destination, 0x80, 0xa0);
                if (matches.getMatchCount() != 4) throw new AssertionError("Expected four VT matches");
                commit = true;
            }
            finally {
                session.endTransaction(tx, commit);
            }
            root.createFile("UI Specialist VT", session, monitor);
            session.save("Saved specialist session", monitor);
        }
        finally {
            session.release(this);
        }
        VTSession reopened = (VTSession) root.getFile("UI Specialist VT").getDomainObject(
            this, false, false, monitor);
        try {
            Map<String, Integer> statuses = new TreeMap<>();
            for (VTMatch m : reopened.getManualMatchSet().getMatches()) {
                statuses.merge(m.getAssociation().getStatus().name(), 1, Integer::sum);
            }
            if (!statuses.equals(Map.of("ACCEPTED", 1, "REJECTED", 1, "AVAILABLE", 2))) {
                throw new AssertionError("Unexpected saved VT statuses: " + statuses);
            }
            Files.writeString(output.resolve("version-tracking.txt"),
                "Manual fixture matches (not correlator accuracy evidence): " + statuses + "\n" +
                "Accepted: 00401000 -> 00401000 (unapplied name/signature markup)\n" +
                "Rejected: 00401040 -> 00401040\n" +
                "Ambiguous available: 00401080 -> 00401080 / 004010a0\n");
        }
        finally {
            reopened.release(this);
        }
    }

    private VTMatch match(VTMatchSet set, Program source, Program destination,
            long from, long to) {
        VTMatchInfo info = new VTMatchInfo(set);
        info.setAssociationType(VTAssociationType.FUNCTION);
        info.setSourceAddress(source.getAddressFactory().getDefaultAddressSpace().getAddress(0x401000 + from));
        info.setDestinationAddress(destination.getAddressFactory().getDefaultAddressSpace().getAddress(0x401000 + to));
        info.setSourceLength((int) source.getFunctionManager().getFunctionAt(info.getSourceAddress()).getBody().getNumAddresses());
        info.setDestinationLength((int) destination.getFunctionManager().getFunctionAt(info.getDestinationAddress()).getBody().getNumAddresses());
        info.setSimilarityScore(new VTScore(1.0));
        info.setConfidenceScore(new VTScore(10.0));
        return set.addMatch(info);
    }

    private void createBSim(Program source, Program destination, Path output) throws Exception {
        BSimServerInfo server = new BSimServerInfo(output.resolve("ui-specialist-bsim").toString());
        try (FunctionDatabase db = BSimClientFactory.buildClient(server, false)) {
            CreateDatabase create = new CreateDatabase();
            create.info = new DatabaseInformation();
            create.info.databasename = "UI Specialist BSim";
            create.info.trackcallgraph = true;
            create.config_template = "medium_nosize";
            if (create.execute(db) == null) throw new AssertionError(db.getLastError().message);
            if (!db.initialize()) throw new AssertionError(db.getLastError().message);
            List<String> report = new ArrayList<>();
            report.add("Local H2 database: " + server.toURLString());
            for (Program program : List.of(source, destination)) {
                GenSignatures signatures = new GenSignatures(true);
                try {
                    signatures.setVectorFactory(db.getLSHVectorFactory());
                    var url = program.getDomainFile().getLocalProjectURL(null);
                    signatures.openProgram(program, null, null, null,
                        ghidra.framework.protocol.ghidra.GhidraURL.getProjectURL(url).toString(),
                        program.getDomainFile().getParent().getPathname());
                    var functions = program.getFunctionManager();
                    signatures.scanFunctions(functions.getFunctions(true), functions.getFunctionCount(), monitor);
                    var descriptions = signatures.getDescriptionManager();
                    if (descriptions.numFunctions() < 3) throw new AssertionError("Insufficient BSim signatures");
                    descriptions.listAllFunctions().forEachRemaining(f -> f.sortCallgraph());
                    InsertRequest insert = new InsertRequest();
                    insert.manage = descriptions;
                    if (insert.execute(db) == null) throw new AssertionError(db.getLastError().message);
                    report.add(program.getName() + ": " + descriptions.numFunctions() + " signatures");
                    QueryNearest query = new QueryNearest();
                    query.manage = descriptions;
                    query.thresh = 0.7;
                    query.signifthresh = 0.0;
                    ResponseNearest response = query.execute(db);
                    if (response == null) throw new AssertionError(db.getLastError().message);
                    if (response.result.isEmpty()) throw new AssertionError("BSim query returned no results");
                    report.add("Verified nearest query result groups: " + response.result.size());
                }
                finally {
                    signatures.dispose();
                }
            }
            ResponseExe count = new QueryExeCount().execute(db);
            if (count == null || count.recordCount != 2) throw new AssertionError("Expected two BSim executables");
            report.add("Verified executable count: " + count.recordCount);
            Files.write(output.resolve("bsim.txt"), report);
        }
    }
}
