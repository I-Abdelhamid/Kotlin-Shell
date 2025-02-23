package com.shell.console

import com.shell.util.PathCommandsLoader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsoleReaderTest {
    private lateinit var consoleReader: ConsoleReader
    private lateinit var pathCommandsLoader: PathCommandsLoader
    private val originalOut = System.out
    private lateinit var outContent: ByteArrayOutputStream

    @BeforeAll
    fun setup() {
        pathCommandsLoader = mock(PathCommandsLoader::class.java)
        `when`(pathCommandsLoader.load(anyString())).thenReturn(mapOf("ls" to "/bin/ls", "cat" to "/bin/cat"))
        consoleReader = ConsoleReader(pathCommandsLoader)
        outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    fun reset() {
        outContent.reset()
        // Reset internal state via reflection if needed
        consoleReader.javaClass.getDeclaredField("buffer").apply {
            isAccessible = true
            set(consoleReader, StringBuilder())
        }
        consoleReader.javaClass.getDeclaredField("cursorPosition").apply {
            isAccessible = true
            setInt(consoleReader, 0)
        }
        consoleReader.javaClass.getDeclaredField("lastTabPressTime").apply {
            isAccessible = true
            set(consoleReader, null)
        }
        consoleReader.javaClass.getDeclaredField("lastTabInput").apply {
            isAccessible = true
            set(consoleReader, null)
        }
    }

    @AfterAll
    fun tearDown() {
        System.setOut(originalOut)
    }

    @Test
    fun `test isValidForCompletion at end with non-empty input`() {
        consoleReader.javaClass.getDeclaredField("cursorPosition").apply {
            isAccessible = true
            setInt(consoleReader, 3)
        }
        assertTrue(consoleReader.isValidForCompletion("cat", "cat"))
    }

    @Test
    fun `test isValidForCompletion not at end`() {
        consoleReader.javaClass.getDeclaredField("cursorPosition").apply {
            isAccessible = true
            setInt(consoleReader, 1)
        }
        assertFalse(consoleReader.isValidForCompletion("cat", "cat"))
    }

    @Test
    fun `test extractWordInfo with single word`() {
        val (word, prefix) = consoleReader.extractWordInfo(listOf("cat"), "cat")
        assertEquals("cat", word)
        assertEquals("", prefix)
    }

    @Test
    fun `test extractWordInfo with trailing space`() {
        val (word, prefix) = consoleReader.extractWordInfo(listOf("echo", "hello"), "echo hello ")

        assertEquals("", word) // Expected to be an empty string
        assertEquals("echo ", prefix) // Expected to be "echo " (with space)
    }

    @Test
    fun `test getCompletions with partial match`() {
        val completions = consoleReader.getCompletions("c")
        assertEquals(listOf("cat", "cd"), completions)
    }

    @Test
    fun `test longestCommonPrefix with multiple strings`() {
        val prefix = consoleReader.longestCommonPrefix(listOf("cat", "catch", "cater"))
        assertEquals("cat", prefix)
    }

    @Test
    fun `test completeSingle adds completion and space`() {
        val bufferField = consoleReader.javaClass.getDeclaredField("buffer").apply {
            isAccessible = true
            set(consoleReader, StringBuilder("c"))
        }
        consoleReader.javaClass.getDeclaredField("cursorPosition").apply {
            isAccessible = true
            setInt(consoleReader, 1)
        }
        consoleReader.completeSingle("cat", "c", "c")
        assertEquals("cat ", bufferField.get(consoleReader).toString())
        assertTrue(outContent.toString().contains("cat "))
    }

    @Test
    fun `test handleCompletions with no matches`() {
        consoleReader.javaClass.getDeclaredField("buffer").apply {
            isAccessible = true
            set(consoleReader, StringBuilder("xyz"))
        }
        consoleReader.javaClass.getDeclaredField("cursorPosition").apply {
            isAccessible = true
            setInt(consoleReader, 3)
        }
        consoleReader.handleCompletions("xyz", emptyList(), "", "xyz")
        assertTrue(outContent.toString().contains("\u0007")) // Beep
    }

    @Test
    fun `test handleMultipleCompletions with single tab`() {
        val bufferField = consoleReader.javaClass.getDeclaredField("buffer").apply {
            isAccessible = true
            set(consoleReader, StringBuilder("c"))
        }
        consoleReader.javaClass.getDeclaredField("cursorPosition").apply {
            isAccessible = true
            setInt(consoleReader, 1)
        }
        consoleReader.handleMultipleCompletions(listOf("cat", "cd"), "c", "c")
        assertEquals("c", bufferField.get(consoleReader).toString()) // Expect "c", not "cat"
    }

    @Test
    fun `test handleMultipleCompletions with double tab`() {
        consoleReader.javaClass.getDeclaredField("buffer").apply {
            isAccessible = true
            set(consoleReader, StringBuilder("c"))
        }
        consoleReader.javaClass.getDeclaredField("cursorPosition").apply {
            isAccessible = true
            setInt(consoleReader, 1)
        }
        consoleReader.javaClass.getDeclaredField("lastTabPressTime").apply {
            isAccessible = true
            set(consoleReader, System.currentTimeMillis() - 500) // Within 1000ms
        }
        consoleReader.javaClass.getDeclaredField("lastTabInput").apply {
            isAccessible = true
            set(consoleReader, "c")
        }
        consoleReader.handleMultipleCompletions(listOf("cat", "cd"), "c", "c")
        assertTrue(outContent.toString().contains("cat  cd"))
    }
}
