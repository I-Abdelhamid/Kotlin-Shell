package com.shell.parser

import com.shell.util.Redirections
import commands.Command
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InputParserTest {
    private lateinit var parser: InputParser
    private val pathCommands = mapOf("ls" to "/bin/ls", "cat" to "/bin/cat")

    @BeforeEach
    fun setup() {
        parser = InputParser()
    }

    // Test parse() method - Command parsing
    @Test
    fun `test parse with exit command`() {
        val (command, redirects) = parser.parse("exit", pathCommands)
        assertTrue(command is Command.Exit)
        assertEquals(Redirections(), redirects)
    }

    @Test
    fun `test parse with cd command and argument`() {
        val (command, redirects) = parser.parse("cd /home", pathCommands)
        assertTrue(command is Command.Cd)
        assertEquals("/home", (command as Command.Cd).directory)
        assertEquals(Redirections(), redirects)
    }

    @Test
    fun `test parse with echo command`() {
        val (command, redirects) = parser.parse("echo Hello World", pathCommands)
        assertTrue(command is Command.Echo)
        assertEquals("Hello World", (command as Command.Echo).text)
        assertEquals(Redirections(), redirects)
    }

    @Test
    fun `test parse with unknown command`() {
        val (command, redirects) = parser.parse("unknown", pathCommands)
        assertTrue(command is Command.Unknown)
        assertEquals("unknown", (command as Command.Unknown).input)
        assertEquals(Redirections(), redirects)
    }

    @Test
    fun `test parse with external command`() {
        val (command, redirects) = parser.parse("ls -l", pathCommands)
        assertTrue(command is Command.ExternalCommand)
        assertEquals(listOf("ls", "-l"), (command as Command.ExternalCommand).args)
        assertEquals(Redirections(), redirects)
    }

    // Test parseRedirections() method
    @Test
    fun `test parseRedirections with stdout redirection`() {
        val (commandPart, redirects) = parser.parseRedirections("echo Hello > output.txt")
        assertEquals("echo Hello", commandPart)
        assertEquals(Redirections(stdout = "output.txt"), redirects)
    }

    @Test
    fun `test parseRedirections with stdout append`() {
        val (commandPart, redirects) = parser.parseRedirections("echo Hello >> output.txt")
        assertEquals("echo Hello", commandPart)
        assertEquals(Redirections(stdout = "output.txt", stdoutAppend = true), redirects)
    }

    @Test
    fun `test parseRedirections with stderr redirection`() {
        val (commandPart, redirects) = parser.parseRedirections("ls 2> error.txt")
        assertEquals("ls", commandPart)
        assertEquals(Redirections(stderr = "error.txt"), redirects)
    }

    @Test
    fun `test parseRedirections with both stdout and stderr`() {
        val (commandPart, redirects) = parser.parseRedirections("cat file > out.txt 2> err.txt")
        assertEquals("cat file", commandPart)
        assertEquals(Redirections(stdout = "out.txt", stderr = "err.txt"), redirects)
    }

    @Test
    fun `test parseRedirections with no redirection`() {
        val (commandPart, redirects) = parser.parseRedirections("pwd")
        assertEquals("pwd", commandPart)
        assertEquals(Redirections(), redirects)
    }

    // Test parseArguments() method
    @Test
    fun `test parseArguments with simple arguments`() {
        val args = parser.parseArguments("ls -l -a")
        assertEquals(listOf("ls", "-l", "-a"), args)
    }

    @Test
    fun `test parseArguments with single quotes`() {
        val args = parser.parseArguments("echo 'Hello World' 'test'")
        assertEquals(listOf("echo", "Hello World", "test"), args)
    }

    @Test
    fun `test parseArguments with double quotes and escapes`() {
        val args = parser.parseArguments("""echo "Hello \"World\"" """)
        assertEquals(listOf("echo", "Hello \"World\""), args)
    }

    @Test
    fun `test parseArguments with backslash outside quotes`() {
        val args = parser.parseArguments("echo Hello\\ World")
        assertEquals(listOf("echo", "Hello World"), args)
    }

    @Test
    fun `test parseArguments with mixed quotes and spaces`() {
        val args = parser.parseArguments("""cat 'file one' "file \"two\"" three""")
        assertEquals(listOf("cat", "file one", "file \"two\"", "three"), args)
    }

    @Test
    fun `test parseArguments with empty input`() {
        val args = parser.parseArguments("")
        assertEquals(emptyList<String>(), args)
    }

    @Test
    fun `test parseArguments with only whitespace`() {
        val args = parser.parseArguments("   \t  ")
        assertEquals(emptyList<String>(), args)
    }
}
