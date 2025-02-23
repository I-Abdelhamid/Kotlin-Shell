import com.shell.Shell
import com.shell.util.Redirections
import commands.Command
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShellTest {
    private lateinit var shell: Shell
    private val originalOut = System.out
    private val originalErr = System.err
    private lateinit var outContent: ByteArrayOutputStream
    private lateinit var errContent: ByteArrayOutputStream

    @BeforeAll
    fun setup() {
        shell = Shell()
        outContent = ByteArrayOutputStream()
        errContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))
        System.setErr(PrintStream(errContent))
    }

    @AfterEach
    fun resetStreams() {
        outContent.reset()
        errContent.reset()
    }

    @AfterAll
    fun tearDown() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Test
    fun `test changeDirectory with valid relative path`() {
        val initialDir = shell.javaClass.getDeclaredField("currentDirectory").apply {
            isAccessible = true
        }.get(shell).toString()

        shell.javaClass.getDeclaredMethod("changeDirectory", String::class.java).apply {
            isAccessible = true
            invoke(shell, "src")
        }

        val newDir = shell.javaClass.getDeclaredField("currentDirectory").apply {
            isAccessible = true
        }.get(shell).toString()

        assertTrue(newDir.endsWith("src"))
        assertNotEquals(initialDir, newDir)
    }

    @Test
    fun `test changeDirectory with invalid path`() {
        shell.javaClass.getDeclaredMethod("changeDirectory", String::class.java).apply {
            isAccessible = true
            invoke(shell, "nonexistent_dir")
        }
        assertTrue(errContent.toString().contains("cd: nonexistent_dir: No such file or directory"))
    }

    @Test
    fun `test handleTypeCommand with built-in command`() {
        val result = shell.javaClass.getDeclaredMethod("handleTypeCommand", String::class.java, Map::class.java)
            .apply {
                isAccessible = true
            }.invoke(shell, "cd", emptyMap<String, String>()) as String

        assertEquals("cd is a shell builtin", result)
    }

    @Test
    fun `test executeCommand with echo and redirection`() {
        val tempFile = File.createTempFile("test", ".txt")
        tempFile.deleteOnExit()

        val command = Command.Echo("Hello World")
        val redirects = Redirections(stdout = tempFile.absolutePath)

        shell.executeCommand(command, emptyMap(), redirects)

        val content = Files.readString(tempFile.toPath())
        assertEquals("Hello World\n", content)
    }

    @Test
    fun `test handleKillCommand with invalid PID`() {
        shell.javaClass.getDeclaredMethod("handleKillCommand", String::class.java, OutputStream::class.java)
            .apply {
                isAccessible = true
            }.invoke(shell, "abc", null)

        assertTrue(errContent.toString().contains("kill: abc: Invalid PID"))
    }

    @Test
    fun `test createFileWithDirs creates directories and file`() {
        val tempDir = Files.createTempDirectory("shell_test").toFile()
        tempDir.deleteOnExit()

        val filePath = "${tempDir.absolutePath}/nested/output.txt"
        val result = shell.createFileWithDirs(filePath, false)

        assertNotNull(result)
        assertTrue(File(filePath).exists())
        result?.close()
    }

    @Test
    fun `test executeCommand with unknown command`() {
        val command = Command.Unknown("nonexistent")
        val redirects = Redirections()

        shell.executeCommand(command, emptyMap(), redirects)

        assertTrue(errContent.toString().contains("nonexistent: command not found"))
    }
}
