package net.microfalx.bootstrap.cli.util;

import net.microfalx.jvm.process.ProcessLauncher;
import net.microfalx.lang.*;

import java.io.File;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;

/**
 * Base class for tools executed as part of a command.
 */
public abstract class Tool<T extends Tool<T>> implements Identifiable<String>, Nameable, Descriptable {

    private final String id;
    private final String name;
    private boolean dryRun;
    private boolean clean;
    private boolean yes;
    private File workingDirectory;

    private Console console;
    protected final Logger logger = Logger.create();

    public Tool(String id, String name) {
        requireNotEmpty(id);
        requireNotEmpty(name);
        this.id = id;
        this.name = name;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return "";
    }

    /**
     * Returns the log of the build process.
     *
     * @return a non-null instance
     */
    public final String getLog() {
        return logger.getOutput();
    }

    /**
     * Returns whether the tool can process the specified directory.
     *
     * @param directory the directory
     * @return {@code true} if the tool can be used, {@code false} otherwise
     */
    public final boolean accept(File directory) {
        requireNonNull(directory);
        for (String testFile : getFiles()) {
            File file = new File(directory, testFile);
            if (!file.exists()) return false;
        }
        return true;
    }

    /**
     * Returns whether the tool has a custom workspace set.
     *
     * @return {@code true} if a custom workspace is set, {@code false} to use the process working directory
     */
    public final boolean hasWorkingDirectory() {
        return workingDirectory != null;
    }

    /**
     * Returns the working directory.
     *
     * @return a non-null instance
     */
    public final File getWorkingDirectory() {
        return workingDirectory != null ? workingDirectory : JvmUtils.getWorkingDirectory();
    }

    /**
     * Changes the working directory.
     *
     * @param workingDirectory the working directory
     * @return self
     */
    public final T setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        return self();
    }

    /**
     * Returns whether the build runs in "dry-run" mode.
     *
     * @return {@code true} if dry run, {@code false} otherwise
     */
    public final boolean isDryRun() {
        return dryRun;
    }

    /**
     * Changes whether the tool simulates the execution.
     *
     * @param dryRun {@code true} for dry run, {@code false} otherwise
     * @return self
     */
    public final T setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return self();
    }

    /**
     * Returns whether the cleaning is performed.
     *
     * @return {@code true} to cleanup, {@code false} otherwise
     */
    public final boolean isClean() {
        return clean;
    }

    /**
     * Changes whether the cleaning is performed.
     *
     * @param clean {@code true} to clean, {@code false} otherwise
     * @return self
     */
    public final T setClean(boolean clean) {
        this.clean = clean;
        return self();
    }

    /**
     * Returns whether the tool will answer to questions automatically.
     *
     * @return {@code true} to cleanup, {@code false} otherwise
     */
    public final boolean isYes() {
        return yes;
    }

    /**
     * Changes whether the auto-answer is enabled.
     *
     * @param yes {@code true} to clean, {@code false} otherwise
     * @return self
     */
    public final T setYes(boolean yes) {
        this.yes = yes;
        return self();
    }

    /**
     * Returns the console associated with this build tool.
     *
     * @return a non-null instance
     */
    public Console getConsole() {
        if (console == null) console = Console.get();
        return console;
    }

    /**
     * Changes the console associated with this build tool.
     *
     * @param console the new console
     * @return self
     */
    public final T setConsole(Console console) {
        this.console = requireNonNull(console);
        return self();
    }

    /**
     * Creates a process launcher for the executable associated with this tool.
     *
     * @return a non-null instance
     */
    public final ProcessLauncher createLauncher() {
        return ProcessLauncher.create(getExecutable())
                .setWorkingDirectory(getWorkingDirectory())
                .setDryRun(dryRun);
    }

    /**
     * Returns the executable name.
     *
     * @return a non-null instance
     */
    protected abstract String getExecutable();

    /**
     * Returns the files required to be in the working directory for the tool to be selected.
     *
     * @return a non-null instance
     */
    protected abstract String[] getFiles();

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }


    /**
     * Subclasses can customize the process launcher.
     *
     * @param launcher the launcher
     */
    protected void updateLauncher(ProcessLauncher launcher) {
        launcher.setName(getName());
    }
}
