package net.microfalx.bootstrap.cli.util;

import net.microfalx.lang.Glyph;
import net.microfalx.threadpool.ThreadPool;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.*;
import static net.microfalx.lang.ThreadUtils.sleepMillis;

/**
 * Emulates a console.
 */
public final class Console {

    private final static CommandLine.Help.Ansi ANSI = CommandLine.Help.Ansi.AUTO;

    private PrintWriter writer;
    private Reader reader;
    private Scanner scanner;
    private java.io.Console console;

    private int tabSize = 2;
    private int indent = 0;

    private static volatile Console instance;

    private final AtomicInteger errorCount = new AtomicInteger();

    /**
     * Returns the current JVM console.
     *
     * @return a non-null instance
     */
    public static Console get() {
        if (instance == null) instance = new Console();
        return instance;
    }

    Console() {
        init();
    }

    Console(PrintWriter writer, Reader reader) {
        this.writer = requireNonNull(writer);
        this.reader = requireNonNull(reader);
    }

    /**
     * Returns the number of failures encountered during writes.
     *
     * @return a positive integer
     */
    public int getErrorCount() {
        return errorCount.get();
    }

    /**
     * Reads a line from the console.
     *
     * @return the line
     */
    public String readLine() {
        if (console != null) {
            return console.readLine();
        } else {
            return scanner.nextLine();
        }
    }

    /**
     * Reads a password from the console.
     *
     * @return the line
     */
    public char[] readPassword() {
        if (console != null) {
            return console.readPassword();
        } else {
            return scanner.nextLine().toCharArray();
        }
    }

    /**
     * Returns the writer of the console.
     *
     * @return a non-null instance
     * @throws IOException if an I/O error occurs
     */
    public PrintWriter getWriter() throws IOException {
        return writer;
    }

    /**
     * Prints 3 dots  to the console.
     *
     * @return self
     */
    public Console printDots() {
        return print("...");
    }

    /**
     * Prints a single quote to the console.
     *
     * @return self
     */
    public Console printQuote() {
        return print("'");
    }

    /**
     * Prints a single space to the console.
     *
     * @return self
     */
    public Console printSpace() {
        return print(" ");
    }

    /**
     * Prints a line to the console, no new line after.
     *
     * @param message the message to print
     * @return self
     */
    public Console print(String message) {
        return writeAndFlush(message);
    }

    /**
     * Moves to a new line.
     *
     * @return self
     */
    public Console printLn() {
        return printLn(EMPTY_STRING);
    }

    /**
     * Prints a line to the console and adds a new line after.
     *
     * @param message the message to print
     * @return self
     */
    public Console printLn(String message) {
        return writeAndFlush(message + "\n");
    }

    /**
     * Prints a message with markup.
     *
     * @param text the text
     * @return self
     */
    public Console printWithMarkup(String text) {
        return print(ANSI.string(text));
    }

    /**
     * Prints a message to with a green color to signal the outcome is "positive".
     *
     * @param text the text
     * @return self
     */
    public Console printOk(String text) {
        String value = ANSI.string("@|green " + defaultIfEmpty(text, NA_STRING) + "|@");
        return print(value);
    }

    /**
     * Prints a message with a red color to signal the outcome is "negative" (a problem).
     *
     * @param text the text
     * @return self
     */
    public Console printFailure(String text) {
        String value = ANSI.string("@|red " + defaultIfEmpty(text, NA_STRING) + "|@");
        return print(value);
    }

    /**
     * Prints a message with a yellow color to signal the outcome is "problematic" (a posible problem).
     *
     * @param text the text
     * @return self
     */
    public Console printWarning(String text) {
        String value = ANSI.string("@|yellow " + defaultIfEmpty(text, NA_STRING) + "|@");
        return print(value);
    }

    /**
     * Prints a bolded message to signal an important part of the message.
     *
     * @param text the text
     * @return self
     */
    public Console printBold(String text) {
        String value = ANSI.string("@|bold " + defaultIfEmpty(text, NA_STRING) + "|@");
        return print(value);
    }

    public Console printTab() {
        String spaces = getStringOfChar(' ', tabSize);
        print(spaces);
        return this;
    }

    public Console printCheck() {
        print(Glyph.CHECK_HEAVY).print(SPACE);
        return this;
    }

    public Console printCross() {
        print(Glyph.CROSS_MARK).print(SPACE);
        return this;
    }

    public Console printBullet() {
        print(Glyph.BULLET).print(SPACE);
        return this;
    }

    public Console printTriangle() {
        print(Glyph.WARNING).print(SPACE);
        return this;
    }

    public Console printRightArrow() {
        print(Glyph.ARROW_RIGHT).print(SPACE);
        return this;
    }

    public Console printLeftArrow() {
        print(Glyph.ARROW_LEFT).print(SPACE);
        return this;
    }

    /**
     * Prints a number of tabs/indents based on the current indent size.
     *
     * @return self
     */
    public Console printList() {
        for (int index = 0; index < indent; index++) {
            printTab();
        }
        return printBullet();
    }

    /**
     * Prints the exit code explanation to the console and a new line.
     *
     * @param code the exit code
     * @return self
     */
    public Console printExitCode(int code) {
        return printExitCode(code, true);
    }

    /**
     * Prints the exit code explanation to the console.
     *
     * @param code    the exit code
     * @param newLine {@code true} if a new line should be added, {@code false} otherwise
     * @return self
     */
    public Console printExitCode(int code, boolean newLine) {
        if (code == 0) {
            printOk("OK");
        } else {
            printFailure("Failed (exit code=" + code + ")");
        }
        if (newLine) printLn();
        return this;
    }

    /**
     * Increases the indent used for lists.
     */
    public Console increaseIndent() {
        indent++;
        return this;
    }

    /**
     * Decreases the indent used for lists.
     */
    public Console decreaseIndent() {
        indent = Math.max(0, indent - 1);
        return this;
    }

    /**
     * Executed a task while printing "." in the console.
     *
     * @param runnable the runnable
     */
    public void execute(Runnable runnable) {
        PrintDotTask task = new PrintDotTask();
        ThreadPool.get().execute(task);
        try {
            runnable.run();
        } finally {
            task.cancel();
        }
    }

    /**
     * Executed a task while printing "." in the console.
     *
     * @param supplier the supplier
     */
    public <T> T execute(Supplier<T> supplier) {
        PrintDotTask task = new PrintDotTask();
        ThreadPool.get().execute(task);
        try {
            return supplier.get();
        } finally {
            task.cancel();
        }
    }

    private Console writeAndFlush(String message) {
        try {
            PrintWriter writer = getWriter();
            writer.write(message);
            writer.flush();
        } catch (IOException e) {
            errorCount.incrementAndGet();
        }
        return this;
    }

    private void init() {
        writer = new PrintWriter(System.out, true);
        console = System.console();
        if (console != null) {
            writer = console.writer();
            reader = console.reader();
        } else {
            writer = new PrintWriter(System.out, true);
            reader = new InputStreamReader(System.in);
        }
        scanner = new Scanner(reader);
    }

    private class PrintDotTask implements Runnable {

        private final AtomicBoolean running = new AtomicBoolean(true);

        private float sleep = 1000;

        @Override
        public void run() {
            while (running.get()) {
                sleepMillis(sleep);
                if (running.get()) print(".");
                sleep = (float) Math.max(5f, sleep * 1.2);
            }
        }

        void cancel() {
            running.set(false);
        }
    }

}
