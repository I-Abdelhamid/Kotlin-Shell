package com.shell.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PathCommandsLoaderTest {

    private val pathCommandsLoader = PathCommandsLoader()

    @Test
    fun `should load commands from valid directories`(@TempDir tempDir: File) {
        val executableFile = File(tempDir, "test-command.sh").apply {
            writeText("#!/bin/bash\necho Hello")
            setExecutable(true)
        }

        val commands = pathCommandsLoader.load(tempDir.absolutePath)

        assertTrue(commands.containsKey("test-command.sh"))
        assertEquals(executableFile.absolutePath, commands["test-command.sh"])
    }

    @Test
    fun `should ignore non-executable files`(@TempDir tempDir: File) {
        File(tempDir, "non-executable.txt").writeText("Hello")

        val commands = pathCommandsLoader.load(tempDir.absolutePath)

        assertTrue(commands.isEmpty()) // No executables should be loaded
    }

    @Test
    fun `should handle multiple paths`(@TempDir tempDir1: File, @TempDir tempDir2: File) {
        val file1 = File(tempDir1, "cmd1.sh").apply {
            writeText("#!/bin/bash\necho Test1")
            setExecutable(true)
        }

        val file2 = File(tempDir2, "cmd2.sh").apply {
            writeText("#!/bin/bash\necho Test2")
            setExecutable(true)
        }

        val commands = pathCommandsLoader.load("${tempDir1.absolutePath}${File.pathSeparator}${tempDir2.absolutePath}")

        assertEquals(file1.absolutePath, commands["cmd1.sh"])
        assertEquals(file2.absolutePath, commands["cmd2.sh"])
    }

    @Test
    fun `should return empty map for non-existent directories`() {
        val commands = pathCommandsLoader.load("/non/existent/path")

        assertTrue(commands.isEmpty())
    }
}
