package com.personalai.platform.configuration;

import static org.junit.jupiter.api.Assertions.*;

import com.personalai.platform.configuration.RuntimeDataSettings.Category;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeDataSettingsTest {
    @TempDir Path temporary;

    @Test
    void defaultsLiveUnderTheUsersHomeAndNeverTheRepository() throws IOException {
        var repository = repository("repository");
        var home = temporary.resolve("home");
        var settings = settings("demo", "", repository, home);
        assertEquals(home.resolve(".sai/data/demo"), settings.dataDirectory());
        assertFalse(settings.dataDirectory().startsWith(repository));
        assertFalse(Files.exists(settings.dataDirectory()), "Configuration must not create directories on startup.");
    }

    @Test
    void everyModeAddsItsOwnNamespaceEvenWithTheSameConfiguredBase() throws IOException {
        var repository = repository("repository");
        var base = temporary.resolve("private");
        var demo = settings("demo", base.toString(), repository, temporary);
        var personal = settings("personal", base.toString(), repository, temporary);
        assertEquals(base.resolve("demo"), demo.dataDirectory());
        assertEquals(base.resolve("personal"), personal.dataDirectory());
        assertNotEquals(demo.directory(Category.UPLOADS), personal.directory(Category.UPLOADS));
    }

    @Test
    void rejectsRelativePathsInvalidModesAndExcessiveUploadLimits() throws IOException {
        var repository = repository("repository");
        assertThrows(IllegalStateException.class, () -> settings("demo", "private", repository, temporary));
        assertThrows(IllegalStateException.class, () -> settings("test", "", repository, temporary));
        assertThrows(IllegalStateException.class, () -> new RuntimeDataSettings("demo", "", "", 0,
                temporary, repository, repository));
    }

    @Test
    void rejectsRepositoryPathsWithGitDirectoriesAndWorktreeGitFiles() throws IOException {
        var repository = repository("repository");
        assertThrows(IllegalStateException.class,
                () -> settings("personal", repository.resolve("ignored-private").toString(), repository, temporary));
        var worktree = Files.createDirectories(temporary.resolve("worktree"));
        Files.writeString(worktree.resolve(".git"), "gitdir: elsewhere");
        assertThrows(IllegalStateException.class,
                () -> settings("demo", worktree.resolve("data").toString(), repository, temporary));
    }

    @Test
    void checksTheCodeLocationWhenLaunchedOutsideTheRepository() throws IOException {
        var repository = repository("repository");
        var elsewhere = Files.createDirectories(temporary.resolve("launch"));
        assertThrows(IllegalStateException.class, () -> new RuntimeDataSettings("personal",
                repository.resolve("private").toString(), "", 1024, temporary, elsewhere,
                repository.resolve("backend/target/classes")));
    }

    @Test
    void rejectsAnExplicitRepositoryRootForPackagedApplications() throws IOException {
        var repository = repository("repository");
        var elsewhere = Files.createDirectories(temporary.resolve("launch"));
        assertThrows(IllegalStateException.class, () -> new RuntimeDataSettings("personal",
                repository.resolve("private").toString(), repository.toString(), 1024,
                temporary, elsewhere, elsewhere));
    }

    @Test
    void rejectsSymlinkDestinationsAndFilesThatEscapeTheNamespace() throws IOException {
        var repository = repository("repository");
        var base = Files.createDirectories(temporary.resolve("private"));
        var link = base.resolve("personal");
        createSymlinkOrSkip(link, repository);
        assertThrows(IllegalStateException.class, () -> settings("personal", base.toString(), repository, temporary));
        var settings = settings("demo", base.toString(), repository, temporary);
        var uploads = settings.directory(Category.UPLOADS);
        var target = Files.writeString(repository.resolve("private.txt"), "synthetic fixture");
        createSymlinkOrSkip(uploads.resolve("redirect"), target);
        assertThrows(IOException.class, () -> settings.requireContained(uploads.resolve("redirect")));
        assertThrows(IOException.class, () -> settings.requireContained(base.resolve("outside")));
    }

    @Test
    void rejectsNamespaceReplacementAfterStartup() throws IOException {
        var repository = repository("repository");
        var settings = settings("personal", temporary.resolve("private").toString(), repository, temporary);
        Files.createDirectories(settings.baseDirectory());
        createSymlinkOrSkip(settings.dataDirectory(), temporary.resolve("elsewhere"));
        assertThrows(IllegalStateException.class, () -> settings.directory(Category.UPLOADS));
    }

    @Test
    void privateCredentialsMustAlsoRemainOutsideRepositories() throws IOException {
        var repository = repository("repository");
        var settings = settings("personal", "", repository, temporary);
        assertThrows(IllegalStateException.class, () -> settings.validatePrivatePath(repository.resolve(".env")));
        assertEquals(temporary.resolve("password"), settings.validatePrivatePath(temporary.resolve("password")));
    }

    @Test
    void personalModeRejectsPublicBindingUntilAuthenticationExists() throws IOException {
        var repository = repository("repository");
        var personal = settings("personal", "", repository, temporary);
        assertThrows(IllegalStateException.class, () -> RuntimeDataConfiguration.requirePersonalLoopback(personal, "0.0.0.0"));
        assertThrows(IllegalStateException.class, () -> RuntimeDataConfiguration.requirePersonalLoopback(personal, "::"));
        assertDoesNotThrow(() -> RuntimeDataConfiguration.requirePersonalLoopback(personal, "127.0.0.1"));
        assertDoesNotThrow(() -> RuntimeDataConfiguration.requirePersonalLoopback(personal, "::1"));
    }

    private RuntimeDataSettings settings(String mode, String base, Path repository, Path home) {
        return new RuntimeDataSettings(mode, base, "", 1024, home, repository, repository);
    }

    private Path repository(String name) throws IOException {
        var path = Files.createDirectories(temporary.resolve(name));
        Files.createDirectory(path.resolve(".git"));
        return path;
    }

    private static void createSymlinkOrSkip(Path link, Path target) throws IOException {
        try { Files.createSymbolicLink(link, target); }
        catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for the current OS/account.");
        }
    }
}
