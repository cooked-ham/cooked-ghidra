// Creates a real repository-backed conflict; leaves Bob's merge for UI review.
// Arguments: specialist output directory, loopback server base port.
import java.nio.file.*;

import ghidra.app.script.GhidraScript;
import ghidra.framework.client.*;
import ghidra.framework.data.*;
import ghidra.framework.model.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.SourceType;

public class PrepareUiSharedProjects extends GhidraScript {
    private Path output;
    private int port;
    private RepositoryServerAdapter server;

    @Override
    protected void run() throws Exception {
        output = Paths.get(getScriptArgs()[0]);
        port = Integer.parseInt(getScriptArgs()[1]);
        DefaultProjectData alice = null;
        DefaultProjectData bob = null;
        try {
            alice = new DefaultProjectData(locator("Alice"), connect("ui_alice"), true);
            DomainFile baseline = currentProgram.getDomainFile().getParent().getFile("merge-base.bin");
            DomainFile file = baseline.copyTo(alice.getRootFolder(), monitor);
            file = file.setName("merge.bin");
            file.addToVersionControl("UI fixture common ancestor", false, monitor);
            if (file.getLatestVersion() != 1) throw new AssertionError("Missing common ancestor");
            alice.close();
            alice = null;

            bob = new DefaultProjectData(locator("Bob"), connect("ui_bob"), true);
            file = bob.getRootFolder().getFile("merge.bin");
            if (!file.checkout(false, monitor)) throw new AssertionError("Bob checkout failed");
            edit(file, "private_classify", 45, "Private branch changed this comment");
            bob.close();
            bob = null;

            connect("ui_alice");
            alice = new DefaultProjectData(locator("Alice"), true, false);
            file = alice.getRootFolder().getFile("merge.bin");
            if (!file.checkout(false, monitor)) throw new AssertionError("Alice checkout failed");
            if (file.getCheckouts().length != 2) throw new AssertionError("Expected concurrent checkouts");
            edit(file, "latest_classify", 44, "Latest branch changed this comment");
            file.checkin(new CheckinHandler() {
                public String getComment() { return "UI fixture latest: conflicting byte, function name and comment"; }
                public boolean keepCheckedOut() { return false; }
                public boolean createKeepFile() { return false; }
            }, monitor);
            if (file.getLatestVersion() != 2) throw new AssertionError("Missing latest version");
            alice.close();
            alice = null;

            connect("ui_bob");
            bob = new DefaultProjectData(locator("Bob"), true, false);
            file = bob.getRootFolder().getFile("merge.bin");
            if (!file.isCheckedOut() || file.getVersion() != 1 || file.getLatestVersion() != 2 || !file.canMerge()) {
                throw new AssertionError("Bob does not have a mergeable version-1 checkout against latest version 2");
            }
            Program privateProgram = (Program) file.getDomainObject(this, false, false, monitor);
            try { verify(privateProgram, 45, "private_classify", "Private branch changed this comment"); }
            finally { privateProgram.release(this); }
            Program ancestor = (Program) file.getReadOnlyDomainObject(this, 1, monitor);
            try { verify(ancestor, 42, "source_classify", "Common ancestor comment"); }
            finally { ancestor.release(this); }
            Program latest = (Program) file.getReadOnlyDomainObject(this, 2, monitor);
            try { verify(latest, 44, "latest_classify", "Latest branch changed this comment"); }
            finally { latest.release(this); }
            if (file.getVersionHistory().length != 2) throw new AssertionError("Incorrect version history");
            Files.writeString(output.resolve("shared-projects.ready"),
                "Loopback server base port: " + port + "\nRepository: UI Review Repository\n" +
                "Alice: version 2 checked in (compare immediate 44, latest_classify, latest comment).\n" +
                "Bob: modified version 1 checkout (compare immediate 45, private_classify, private comment).\n" +
                "Common ancestor: compare immediate 42, source_classify, common ancestor comment.\n" +
                "Reopened Bob project: canMerge=true, checkout version=1, latest=2.\n" +
                "UI entry: open Bob.gpr, connect as ui_bob, merge.bin > Check In / Merge.\n" +
                "The merge is intentionally unresolved for UI review.\n");
        }
        finally {
            if (alice != null) alice.close();
            if (bob != null) bob.close();
            if (server != null) server.disconnect();
        }
    }

    private ProjectLocator locator(String name) {
        return new ProjectLocator(output.toString(), name);
    }

    private RepositoryAdapter connect(String user) throws Exception {
        if (server != null) server.disconnect();
        ClientUtil.setClientAuthenticator(new DefaultClientAuthenticator() {
            @Override
            public boolean processPasswordCallbacks(String title, String type, String host,
                    boolean allowName, javax.security.auth.callback.NameCallback name,
                    javax.security.auth.callback.PasswordCallback password,
                    javax.security.auth.callback.ChoiceCallback choice,
                    ghidra.framework.remote.AnonymousCallback anonymous, String error) {
                if (error != null || name == null || password == null) return false;
                name.setName(user);
                password.setPassword("ui-fixture-only".toCharArray());
                return true;
            }
            @Override
            public boolean promptForReconnect(java.awt.Component parent, String message) { return false; }
        });
        server = ClientUtil.getRepositoryServer("127.0.0.1", port, true);
        if (!server.isConnected()) throw new AssertionError("Cannot connect as " + user);
        RepositoryAdapter repository = server.getRepository("UI Review Repository");
        repository.connect();
        if (!repository.getUser().getName().equals(user)) throw new AssertionError("Wrong repository identity");
        return repository;
    }

    private void edit(DomainFile file, String name, int immediate, String comment) throws Exception {
        Program program = (Program) file.getDomainObject(this, false, false, monitor);
        try {
            int tx = program.startTransaction("Conflicting UI fixture edit");
            boolean commit = false;
            try {
                var entry = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x401000);
                var instruction = entry.add(4);
                program.getListing().clearCodeUnits(instruction, instruction.add(2), false);
                program.getMemory().setByte(entry.add(6), (byte) immediate);
                if (!new ghidra.app.cmd.disassemble.DisassembleCommand(instruction, null, true)
                        .applyTo(program, monitor)) throw new AssertionError("Cannot redisassemble variant");
                program.getFunctionManager().getFunctionAt(entry).setName(name, SourceType.USER_DEFINED);
                program.getListing().setComment(entry.add(4), CommentType.EOL, comment);
                commit = true;
            }
            finally { program.endTransaction(tx, commit); }
            program.save("Saved conflict fixture", monitor);
        }
        finally { program.release(this); }
    }

    private void verify(Program program, int immediate, String name, String comment) throws Exception {
        var entry = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x401000);
        if ((program.getMemory().getByte(entry.add(6)) & 255) != immediate ||
                !program.getFunctionManager().getFunctionAt(entry).getName().equals(name) ||
                !comment.equals(program.getListing().getComment(CommentType.EOL, entry.add(4)))) {
            throw new AssertionError("Version content did not persist: " + name);
        }
    }
}
