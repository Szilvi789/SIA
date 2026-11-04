package com.personalai.platform.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AccessDeniedException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private runtime paths. The configured base always receives a separate mode namespace. */
public final class RuntimeDataSettings {
    public enum Mode { DEMO, PERSONAL }

    public enum Category {
        RULES("rules"), KNOWLEDGE("knowledge"), CONVERSATIONS("conversations"), DRAFTS("drafts"),
        UPLOADS("uploads"), GENERATED("generated"), BACKUPS("backups"), CONFIG("config");

        private final String directory;

        Category(String directory) {
            this.directory = directory;
        }
    }

    private final Mode mode;
    private final Path baseDirectory;
    private final Path dataDirectory;
    private final long maximumUploadBytes;
    private final Set<Path> repositoryRoots;
    private final AtomicBoolean permissionsWarningLogged = new AtomicBoolean();

    public RuntimeDataSettings(String mode, String dataDirectory, String repositoryRoot, long maximumUploadBytes) {
        this(mode, dataDirectory, repositoryRoot, maximumUploadBytes,
                Path.of(System.getProperty("user.home")), Path.of(System.getProperty("user.dir")), codeLocation());
    }

    RuntimeDataSettings(String mode, String dataDirectory, String repositoryRoot, long maximumUploadBytes,
            Path userHome, Path workingDirectory, Path codeLocation) {
        try {
            this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("SAI_MODE must be demo or personal.");
        }
        if (maximumUploadBytes < 1 || maximumUploadBytes > 256L * 1024 * 1024) {
            throw new IllegalStateException("SAI_MAX_UPLOAD_BYTES must be between 1 and 268435456.");
        }
        this.maximumUploadBytes = maximumUploadBytes;
        var roots = new LinkedHashSet<Path>();
        discoverRepositories(workingDirectory, roots);
        discoverRepositories(codeLocation, roots);
        if (repositoryRoot != null && !repositoryRoot.isBlank()) {
            var explicitRoot = Path.of(repositoryRoot);
            requireAbsolute(explicitRoot);
            roots.add(canonical(explicitRoot));
        }
        this.repositoryRoots = Set.copyOf(roots);
        var configuredBase = dataDirectory == null || dataDirectory.isBlank()
                ? userHome.resolve(".sai").resolve("data") : Path.of(dataDirectory);
        this.baseDirectory = validatePrivatePath(configuredBase);
        var namespace = this.baseDirectory.resolve(modeName());
        this.dataDirectory = validatePrivatePath(namespace);
        if (!this.dataDirectory.equals(namespace) || Files.isSymbolicLink(namespace)) {
            throw new IllegalStateException("Private mode storage must not be redirected by a symbolic link.");
        }
        if (this.repositoryRoots.stream().anyMatch(root -> root.startsWith(this.dataDirectory))) {
            throw new IllegalStateException("The private data directory must not contain the source repository.");
        }
        rejectNamespaceLinks();
    }

    public Mode mode() { return mode; }
    public String modeName() { return mode.name().toLowerCase(Locale.ROOT); }
    public boolean isPersonal() { return mode == Mode.PERSONAL; }
    public Path baseDirectory() { return baseDirectory; }
    public Path dataDirectory() { return dataDirectory; }
    public long maximumUploadBytes() { return maximumUploadBytes; }

    /** Also usable for credential files held outside the runtime directory. Never prints the path. */
    public Path validatePrivatePath(Path path) {
        requireAbsolute(path);
        var resolved = canonical(path.normalize());
        var roots = new LinkedHashSet<>(repositoryRoots);
        // Catch a different repository at the chosen destination, including worktrees (.git files).
        discoverRepositories(path, roots);
        if (roots.stream().anyMatch(resolved::startsWith)) {
            throw new IllegalStateException("Private runtime data and credentials must be outside Git repositories.");
        }
        return resolved;
    }

    /** Allowlisted categories only; paths supplied by an HTTP client never reach this method. */
    public Path directory(Category category) throws IOException {
        if (category == null) {
            throw new IllegalArgumentException("A private storage category is required.");
        }
        rejectNamespaceLinks();
        var path = dataDirectory.resolve(category.directory);
        requireContained(path);
        createPrivateDirectory(dataDirectory);
        createPrivateDirectory(path);
        requireContained(path);
        return path;
    }

    public void requireContained(Path path) throws IOException {
        rejectNamespaceLinks();
        if (!path.normalize().startsWith(dataDirectory)) {
            throw new IOException("The storage path is outside its private namespace.");
        }
        var current = dataDirectory;
        for (var segment : dataDirectory.relativize(path.normalize())) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not permitted in private storage.");
            }
        }
        var real = validatePrivatePath(path);
        if (!real.startsWith(dataDirectory)) {
            throw new IOException("The storage path is outside its private namespace.");
        }
    }

    /** Restricts new runtime files and directories where the filesystem exposes POSIX or ACL controls. */
    public void restrictToOwner(Path path, boolean directory) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (acl != null) {
                var owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
                try {
                    acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                            .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
                } catch (AccessDeniedException exception) {
                    // Managed Windows accounts may allow file writes but reserve ACL changes to administrators.
                    // Retain inherited ACLs and make the limitation visible without disclosing the private path.
                    if (permissionsWarningLogged.compareAndSet(false, true)) {
                        System.getLogger(RuntimeDataSettings.class.getName()).log(System.Logger.Level.WARNING,
                                "Windows prevented owner-only ACL changes; verify private storage permissions in your OS.");
                    }
                }
            }
        }
    }

    private void createPrivateDirectory(Path path) throws IOException {
        requireContained(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(path);
            restrictToOwner(path, true);
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A private storage directory is unavailable.");
        }
    }

    private void rejectNamespaceLinks() {
        if (Files.isSymbolicLink(dataDirectory) || !canonical(dataDirectory).equals(dataDirectory)) {
            throw new IllegalStateException("Private mode storage must not be redirected by a symbolic link.");
        }
    }

    private static void requireAbsolute(Path path) {
        if (path == null || !path.isAbsolute()) {
            throw new IllegalStateException("Private storage paths must be absolute; SAI_DATA_DIR cannot be relative.");
        }
    }

    private static Path canonical(Path path) {
        requireAbsolute(path);
        var absolute = path.toAbsolutePath().normalize();
        var suffix = new ArrayList<Path>();
        var ancestor = absolute;
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            suffix.add(ancestor.getFileName());
            ancestor = ancestor.getParent();
        }
        try {
            var resolved = ancestor == null ? absolute.getRoot() : ancestor.toRealPath();
            for (int index = suffix.size() - 1; index >= 0; index--) {
                resolved = resolved.resolve(suffix.get(index));
            }
            return resolved.normalize();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to validate a private storage path.");
        }
    }

    private static void discoverRepositories(Path location, Set<Path> roots) {
        if (location == null) { return; }
        var current = location.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                roots.add(canonical(current));
            }
            current = current.getParent();
        }
        // Real ancestry also matters when the working directory was reached through a link.
        var resolved = canonical(location);
        if (!resolved.equals(location.toAbsolutePath().normalize())) {
            current = resolved;
            while (current != null) {
                if (Files.exists(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) { roots.add(current); }
                current = current.getParent();
            }
        }
    }

    private static Path codeLocation() {
        try {
            return Path.of(RuntimeDataSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception exception) {
            return null;
        }
    }
}
