import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    ProcessBuilder("/bin/sh", "-c", "stty raw -echo").inheritIO().start().waitFor()
    try {
        val shell = Shell()
        shell.start()
    } finally {
        ProcessBuilder("/bin/sh", "-c", "stty sane").inheritIO().start().waitFor()
    }
}

class Shell {
    private val commandExecutor = CommandExecutor()
    private val inputParser = InputParser()
    private var currentDirectory = Paths.get("").toAbsolutePath()
    private val reader = ConsoleReader()
    private val env = System.getenv().toMutableMap()

    fun start() {
        var shouldContinue = true
        while (shouldContinue) {
            shouldContinue = promptAndExecute()
        }
    }

    private fun promptAndExecute(): Boolean {
        print("$ ")
        System.out.flush()
        val inputLine = reader.readLine()
        if (inputLine.isBlank()) return true
        if (inputLine == "exit" || inputLine == "exit 0") return false

        try {
            val pathCommands = PathCommandsLoader.load(env["PATH"] ?: "")
            val (parsedCommand, redirects) = inputParser.parse(inputLine, pathCommands)
            print("\r")
            System.out.flush()
            executeCommand(parsedCommand, pathCommands, redirects)
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
        return true
    }

    private class ConsoleReader {
        private val builtins = listOf("echo", "exit", "type", "cd", "pwd", "kill")
        private val buffer = StringBuilder()
        private var cursorPosition = 0

        fun readLine(): String {
            buffer.clear()
            cursorPosition = 0
            while (true) {
                val input = System.`in`.read()
                when (input) {
                    -1 -> return buffer.toString()
                    9 -> handleTabCompletion()
                    13 -> {
                        val result = buffer.toString().trimEnd()
                        print("\n")
                        System.out.flush()
                        buffer.clear()
                        cursorPosition = 0
                        return result
                    }
                    127 -> {
                        if (buffer.isNotEmpty() && cursorPosition > 0) {
                            buffer.deleteCharAt(cursorPosition - 1)
                            cursorPosition--
                            print("\b \b")
                            System.out.flush()
                        }
                    }
                    else -> {
                        val char = input.toChar()
                        if (char.isISOControl()) continue
                        buffer.insert(cursorPosition, char)
                        cursorPosition++
                        print(char)
                        System.out.flush()
                    }
                }
            }
        }

        private fun handleTabCompletion() {
            val currentInput = buffer.toString().trimEnd()
            val completions = builtins.filter { it.startsWith(currentInput) }
            if (completions.size == 1) {
                val completion = completions[0]
                repeat(buffer.length) { print("\b \b") }
                buffer.clear()
                buffer.append(completion)
                cursorPosition = completion.length
                print(completion)
                if (!completion.endsWith(" ")) {
                    buffer.append(" ")
                    cursorPosition++
                    print(" ")
                }
                System.out.flush()
            }
        }
    }

    private fun executeCommand(command: Command, pathCommands: Map<String, String>, redirects: Redirections) {
        fun createFileWithDirs(path: String, append: Boolean): FileOutputStream? {
            return try {
                val file = File(path)
                file.parentFile?.mkdirs()
                FileOutputStream(file, append)
            } catch (e: Exception) {
                System.err.println("Failed to create file: $path")
                null
            }
        }

        val stdoutStream = redirects.stdout?.let { createFileWithDirs(it, redirects.stdoutAppend) }
        val stderrStream = redirects.stderr?.let { createFileWithDirs(it, redirects.stderrAppend) }

        try {
            when (command) {
                is Command.Exit -> {}
                is Command.Cd -> changeDirectory(command.directory)
                is Command.Pwd -> {
                    val output = "$currentDirectory\n"
                    stdoutStream?.write(output.toByteArray()) ?: print(output)
                }
                is Command.Type -> {
                    val output = handleTypeCommand(command.argument, pathCommands) + "\n"
                    stdoutStream?.write(output.toByteArray()) ?: print(output)
                }
                is Command.Echo -> {
                    val output = "${command.text}\n"
                    stdoutStream?.write(output.toByteArray()) ?: print(output)
                }
                is Command.Kill -> handleKillCommand(command.pid, stderrStream)
                is Command.ExternalCommand -> {
                    commandExecutor.execute(command.args, currentDirectory.toFile(), env, stdoutStream, stderrStream)
                }
                is Command.Unknown -> {
                    val errorMsg = "${command.input}: command not found\n"
                    stderrStream?.write(errorMsg.toByteArray()) ?: System.err.print(errorMsg)
                }
            }
        } finally {
            stdoutStream?.close()
            stderrStream?.close()
        }
    }

    private fun changeDirectory(dir: String?) {
        if (dir == null) {
            currentDirectory = Paths.get(env["HOME"] ?: "/")
            return
        }
        val newPath = when {
            dir == "~" -> Paths.get(env["HOME"] ?: "/")
            Paths.get(dir).isAbsolute -> Paths.get(dir)
            else -> currentDirectory.resolve(dir).normalize()
        }
        if (Files.exists(newPath) && Files.isDirectory(newPath)) {
            currentDirectory = newPath
        } else {
            System.err.println("cd: $dir: No such file or directory")
        }
    }

    private fun handleTypeCommand(argument: String?, pathCommands: Map<String, String>): String {
        if (argument == null) return "type: missing argument"
        if (BuiltInCommands.entries.any { it.name.equals(argument, ignoreCase = true) }) {
            return "$argument is a shell builtin"
        }
        return pathCommands[argument]?.let { "$argument is $it" } ?: "$argument: not found"
    }

    private fun handleKillCommand(pid: String?, stderrStream: OutputStream?) {
        if (pid == null) {
            val errorMsg = "kill: missing argument\n"
            stderrStream?.write(errorMsg.toByteArray()) ?: System.err.print(errorMsg)
            return
        }
        try {
            val pidNum = pid.toInt()
            val process = ProcessBuilder("kill", "-9", pid).start()
            process.waitFor()
            if (process.exitValue() != 0) {
                val errorMsg = "kill: $pid: No such process\n"
                stderrStream?.write(errorMsg.toByteArray()) ?: System.err.print(errorMsg)
            }
        } catch (e: NumberFormatException) {
            val errorMsg = "kill: $pid: Invalid PID\n"
            stderrStream?.write(errorMsg.toByteArray()) ?: System.err.print(errorMsg)
        }
    }
}

data class Redirections(
    val stdout: String? = null,
    val stderr: String? = null,
    val stdoutAppend: Boolean = false,
    val stderrAppend: Boolean = false
)

sealed class Command {
    object Exit : Command()
    data class Cd(val directory: String?) : Command()
    object Pwd : Command()
    data class Type(val argument: String?) : Command()
    data class Echo(val text: String) : Command()
    data class Kill(val pid: String?) : Command()
    data class ExternalCommand(val args: List<String>) : Command()
    data class Unknown(val input: String) : Command()
}

enum class BuiltInCommands {
    EXIT, ECHO, TYPE, PWD, CD, KILL
}

object PathCommandsLoader {
    fun load(path: String): Map<String, String> {
        val commands = mutableMapOf<String, String>()
        path.split(File.pathSeparator)
            .map { Paths.get(it).toAbsolutePath().normalize().toString() }
            .forEach { pathDir ->
                val directory = File(pathDir)
                if (directory.exists() && directory.isDirectory) {
                    directory.listFiles()?.filter { it.isFile && it.canExecute() }
                        ?.forEach { file -> commands.putIfAbsent(file.name, file.absolutePath) }
                }
            }
        return commands
    }
}

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
        processBuilder.redirectOutput(if (stdoutStream != null) ProcessBuilder.Redirect.PIPE else ProcessBuilder.Redirect.INHERIT)
        processBuilder.redirectError(if (stderrStream != null) ProcessBuilder.Redirect.PIPE else ProcessBuilder.Redirect.INHERIT)

        val process = processBuilder.start()

        val stdoutThread = stdoutStream?.let {
            Thread {
                process.inputStream.use { it.copyTo(stdoutStream) }
            }.apply { start() }
        }

        val stderrThread = stderrStream?.let {
            Thread {
                process.errorStream.use { it.copyTo(stderrStream) }
            }.apply { start() }
        }

        process.waitFor()
        stdoutThread?.join()
        stderrThread?.join()
    }
}

class InputParser {
    private val SINGLE_QUOTE = '\''
    private val DOUBLE_QUOTE = '"'
    private val BACKSLASH = '\\'

    fun parse(input: String, pathCommands: Map<String, String>): Pair<Command, Redirections> {
        val (commandPart, redirections) = parseRedirections(input)
        val args = parseArguments(commandPart)
        if (args.isEmpty()) return Pair(Command.Unknown(commandPart), redirections)

        val command = when (args[0]) {
            "exit" -> Command.Exit
            "cd" -> Command.Cd(args.getOrNull(1))
            "pwd" -> Command.Pwd
            "type" -> Command.Type(args.getOrNull(1))
            "echo" -> Command.Echo(args.drop(1).joinToString(" "))
            "kill" -> Command.Kill(args.getOrNull(1))
            else -> if (pathCommands.containsKey(args[0])) Command.ExternalCommand(args) else Command.Unknown(commandPart)
        }
        return Pair(command, redirections)
    }

    private fun parseRedirections(input: String): Pair<String, Redirections> {
        var commandPart = input
        var stdout: String? = null
        var stderr: String? = null
        var stdoutAppend = false
        var stderrAppend = false

        // Split input into parts based on redirection operators
        val parts = input.split("\\s*(2>>|2>|>>|>)\\s*".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            commandPart = parts[0]
            for (i in 1 until parts.size step 2) {
                val operator = input.substring(
                    input.indexOf(parts[i - 1]) + parts[i - 1].length,
                    input.indexOf(parts[i])
                ).trim()
                val target = parts[i]
                when (operator) {
                    "2>>" -> {
                        stderr = target
                        stderrAppend = true
                    }
                    "2>" -> stderr = target
                    ">>" -> {
                        stdout = target
                        stdoutAppend = true
                    }
                    ">" -> stdout = target
                }
            }
        }

        return Pair(commandPart.trim(), Redirections(stdout, stderr, stdoutAppend, stderrAppend))
    }

    private fun parseArguments(input: String): List<String> {
        val args = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var quoteChar: Char? = null
        var i = 0

        while (i < input.length) {
            val c = input[i]
            if (inQuotes) {
                when {
                    c == quoteChar -> {
                        inQuotes = false
                        quoteChar = null
                        i++
                    }
                    c == BACKSLASH && quoteChar == DOUBLE_QUOTE && i + 1 < input.length -> {
                        val next = input[i + 1]
                        if (next in setOf(DOUBLE_QUOTE, BACKSLASH)) sb.append(next)
                        i += 2
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            } else {
                when {
                    c.isWhitespace() -> {
                        if (sb.isNotEmpty()) {
                            args.add(sb.toString())
                            sb.clear()
                        }
                        i++
                    }
                    c == SINGLE_QUOTE || c == DOUBLE_QUOTE -> {
                        inQuotes = true
                        quoteChar = c
                        i++
                    }
                    c == BACKSLASH && i + 1 < input.length -> {
                        sb.append(input[i + 1])
                        i += 2
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
        }
        if (sb.isNotEmpty()) args.add(sb.toString())
        return args
    }
}
