package net.microfalx.bootstrap.cli.util;

import net.microfalx.jvm.process.ProcessLauncher;
import net.microfalx.lang.JvmUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolTest {

    private Tool tool = new TestTool();

    @Test
    void getLog() {
        assertEquals("", tool.getLog());
    }

    @Test
    void accept() {
        assertTrue(tool.accept(JvmUtils.getWorkingDirectory()));
    }

    @Test
    void getWorkingDirectory() {
        assertNotNull(tool.getWorkingDirectory());
        tool.setWorkingDirectory(JvmUtils.getWorkingDirectory());
        assertEquals(JvmUtils.getWorkingDirectory(), tool.getWorkingDirectory());
    }

    @Test
    void isDryRun() {
        assertFalse(tool.isDryRun());
        assertTrue(tool.setDryRun(true).isDryRun());
    }

    @Test
    void isClean() {
        assertFalse(tool.isClean());
        assertTrue(tool.setClean(true).isClean());
    }

    @Test
    void getConsole() {
        assertNotNull(tool.getConsole());
        assertNotNull(tool.setConsole(Console.get()).getConsole());
    }

    @Test
    void createLauncher() {
        ProcessLauncher launcher = tool.createLauncher();
        launcher.start(true);
        assertEquals(0, launcher.waitFor());
    }
}