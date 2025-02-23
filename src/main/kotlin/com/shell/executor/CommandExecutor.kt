package com.shell.executor
import java.io.File
import java.io.OutputStream

class CommandExecutor {
    fun execute(
        command: List<String>,
        directory: File,
        env: Map<String, String>,
        stdoutStream: OutputStream?,
        stderrStream: OutputStream?
    ) {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(directory)
        processBuilder.environment().putAll(env)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE)

        val process = processBuilder.start()

        process.inputStream.use { input ->
            if (stdoutStream != null) {
                input.copyTo(stdoutStream)
                stdoutStream.flush()
            } else {
                input.copyTo(System.out)
                System.out.flush()
            }
        }

        process.errorStream.use { error ->
            if (stderrStream != null) {
                error.copyTo(stderrStream)
                stderrStream.flush()
            } else {
                error.copyTo(System.err)
                System.err.flush()
            }
        }

        process.waitFor()
    }
}
