package net.microfalx.bootstrap.cli.util;

import net.microfalx.jvm.process.ProcessLauncher;
import net.microfalx.lang.Nameable;

import java.util.stream.Stream;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;

/**
 * Base class for all tool executions.
 *
 * @param <T> the tool type
 * @param <E> the execution type
 */
public abstract class Execution<T extends Tool<T>, E extends Execution<T, E>> implements Nameable {

    private final T tool;
    private final String name;

    private ProcessLauncher launcher;

    public Execution(T tool, String name) {
        requireNonNull(tool);
        requireNonNull(name);
        this.tool = tool;
        this.name = name;
    }

    @Override
    public final String getName() {
        return name;
    }

    /**
     * Returns the build tool.
     *
     * @return a non-null instance
     */
    public final T getTool() {
        return tool;
    }

    /**
     * Attaches a process to this execution.
     *
     * @param launcher the process launcher
     * @return self
     */
    public final E setLauncher(ProcessLauncher launcher) {
        this.launcher = requireNonNull(launcher);
        return self();
    }

    /**
     * Returns the console associated with the build execution.
     *
     * @return a non-null instance
     */
    public final Console getConsole() {
        return tool.getConsole();
    }

    /**
     * Waits for the execution to complete and returns the exit code.
     *
     * @return a positive integer, 0 = OK
     */
    public int waitFor() {
        beforeExecution();
        Throwable throwable = null;
        try {
            ProcessLauncher currentLauncher = doGetLauncher();
            if (!currentLauncher.isStarted()) currentLauncher.start(true);
            return currentLauncher.waitFor();
        } catch (Exception e) {
            throwable = e;
            return rethrowExceptionAndReturn(e);
        } finally {
            afterExecution(throwable);
        }
    }

    /**
     * Returns the execution date.
     *
     * @return a non-null string
     */
    public final String getLogs() {
        return doGetLauncher().getLogsAsString();
    }

    /**
     * Returns a stream of string (lines) from the process log.
     *
     * @return a non-null instance
     */
    public final Stream<String> getLogsStream() {
        return doGetLauncher().getLogsStream();
    }

    /**
     * Executed before running the process.
     */
    protected void beforeExecution() {
        // empty by default
    }

    /**
     * Executed after the process was executed.
     */
    protected void afterExecution(Throwable throwable) {
        // empty by default
    }

    protected final ProcessLauncher doGetLauncher() {
        if (launcher == null) {
            throw new IllegalStateException("A process launcher has not been set");
        }
        return launcher;
    }

    @SuppressWarnings("unchecked")
    protected final E self() {
        return (E) this;
    }
}
