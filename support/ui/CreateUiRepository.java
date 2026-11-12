// Prepares an offline disposable repository root, with no service installation.
// Argument: specialist output directory. Run only against a new fixture directory.
import java.nio.file.*;

import ghidra.app.script.GhidraScript;
import ghidra.framework.remote.User;
import ghidra.server.RepositoryManager;

public class CreateUiRepository extends GhidraScript {
    @Override
    protected void run() throws Exception {
        Path output = Paths.get(getScriptArgs()[0]);
        Path repositoryRoot = Files.createDirectory(output.resolve("repositories"));
        RepositoryManager manager = new RepositoryManager(repositoryRoot.toFile(), true, 0, false);
        try {
            manager.getUserManager().addUser("ui_alice");
            manager.getUserManager().addUser("ui_bob");
            for (String user : new String[] { "ui_alice", "ui_bob" }) {
                manager.getUserManager().setPassword(user, generic.hash.HashUtilities.getSaltedHash(
                    generic.hash.HashUtilities.SHA256_ALGORITHM, "ui-fixture-only".toCharArray()), false);
            }
            var repository = manager.createRepository("ui_alice", "UI Review Repository");
            repository.setUserList("ui_alice", new User[] {
                new User("ui_alice", User.ADMIN), new User("ui_bob", User.WRITE)
            }, false);
        }
        finally {
            manager.dispose();
        }
        manager = new RepositoryManager(repositoryRoot.toFile(), true, 0, false);
        try {
            var repository = manager.getRepository("ui_alice", "UI Review Repository");
            if (repository.getUserList("ui_alice").length != 2 ||
                    !repository.getUser("ui_bob").hasWritePermission()) {
                throw new AssertionError("Repository users/permissions did not persist");
            }
        }
        finally {
            manager.dispose();
        }
        Files.writeString(output.resolve("repository.ready"),
            "Offline repository root verified; ui_alice=admin, ui_bob=write; fixture password=ui-fixture-only.\n" +
            "No running server, shared project, checkouts or repository-backed merge yet.\n");
    }
}
