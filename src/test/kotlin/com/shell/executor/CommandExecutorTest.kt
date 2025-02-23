import com.shell.executor.CommandExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandExecutorTest {
    private val commandExecutor = CommandExecutor()

    @Test
    fun `should capture stdout output`() {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        commandExecutor.execute(
            listOf("echo", "Hello, World!"),
            File("."),
            emptyMap(),
            stdoutStream,
            stderrStream
        )

        assertEquals("Hello, World!\n", stdoutStream.toString(Charsets.UTF_8))
        assertEquals("", stderrStream.toString(Charsets.UTF_8)) // No error output expected
    }

    @Test
    fun `should capture stderr output`() {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        commandExecutor.execute(
            listOf("sh", "-c", "echo Error! >&2"),
            File("."),
            emptyMap(),
            stdoutStream,
            stderrStream
        )

        assertEquals("", stdoutStream.toString(Charsets.UTF_8)) // No stdout expected
        assertEquals("Error!\n", stderrStream.toString(Charsets.UTF_8))
    }

    @Test
    fun `should handle non-zero exit code`() {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        commandExecutor.execute(
            listOf("sh", "-c", "exit 1"), // This command fails with exit code 1
            File("."),
            emptyMap(),
            stdoutStream,
            stderrStream
        )

        assertEquals("", stdoutStream.toString(Charsets.UTF_8)) // No stdout expected
        assertEquals("", stderrStream.toString(Charsets.UTF_8)) // No stderr output, just failure
    }

    @Test
    fun `should pass environment variables`() {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        commandExecutor.execute(
            listOf("sh", "-c", "echo \"\$TEST_ENV_VAR\""), // Properly reference the environment variable
            File("."),
            mapOf("TEST_ENV_VAR" to "Environment Works!"),
            stdoutStream,
            stderrStream
        )

        assertEquals("Environment Works!\n", stdoutStream.toString(Charsets.UTF_8))
    }

    @Test
    fun `should execute command in specified directory`() {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()
        val tempDir = File(System.getProperty("java.io.tmpdir"))

        commandExecutor.execute(
            listOf("pwd"),
            tempDir,
            emptyMap(),
            stdoutStream,
            stderrStream
        )

        val expectedPath = Paths.get(tempDir.absolutePath).toRealPath().toString()
        val actualPath = Paths.get(stdoutStream.toString(Charsets.UTF_8).trim()).toRealPath().toString()

        assertEquals(expectedPath, actualPath)
    }

    @Test
    fun `should handle long running process`() {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        commandExecutor.execute(
            listOf("sh", "-c", "sleep 2; echo Done!"),
            File("."),
            emptyMap(),
            stdoutStream,
            stderrStream
        )

        assertEquals("Done!\n", stdoutStream.toString(Charsets.UTF_8))
    }

    @Test
    fun `should handle command not found`() {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        val exception = assertThrows<IOException> {
            commandExecutor.execute(
                listOf("nonexistentcommand"), // This command does not exist
                File("."),
                emptyMap(),
                stdoutStream,
                stderrStream
            )
        }

        assertTrue(exception.message!!.contains("No such file or directory") || exception.message!!.contains("error=2"))
    }
}
