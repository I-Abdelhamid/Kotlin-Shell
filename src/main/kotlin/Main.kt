import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    // Enable raw mode (disable echo)
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
        printPrompt()
        while (true) {
            val inputLine = reader.readLine().trimEnd()
            if (inputLine.isBlank()) {
                printPrompt()
                continue
            }
            if (inputLine == "exit" || inputLine == "exit 0") break

            try {
                val pathCommands = PathCommandsLoader.load(env["PATH"] ?: "")
                val (parsedCommand, redirects) = inputParser.parse(inputLine, pathCommands)
                executeCommand(parsedCommand, pathCommands, redirects)
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
            printPrompt()
        }
    }

    private fun printPrompt() {
        print("\r\u001B[K$ ")
        System.out.flush()
    }

    private inner class ConsoleReader {
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
                    10, 13 -> {
                        val result = buffer.toString().trimEnd()
                        print("\r\n\u001B[K")
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
                        if (char.isISOControl() && char != '\t') continue
                        buffer.insert(cursorPosition, char)
                        cursorPosition++
                        print(char)
                        System.out.flush()
                    }
                }
            }
        }

        private fun handleTabCompletion() {
            val currentInput = buffer.toString()
            val trimmedInput = currentInput.trimEnd()

            // Split input into words based on whitespace
            val words = trimmedInput.split("\\s+".toRegex())
            val cursorAtEnd = cursorPosition == currentInput.length

            if (!cursorAtEnd) {
                // If cursor isn't at end, don't perform completion
                return
            }

            if (words.isEmpty()) {
                return
            }

            // If we're completing the first word (command)
            if (words.size == 1 || (words.size > 1 && currentInput.endsWith(" "))) {
                val currentWord = if (currentInput.endsWith(" ")) "" else words.last()
                val previousArgs = if (words.size > 1) words.dropLast(1).joinToString(" ") + " " else ""

                val completions = builtins.filter { it.startsWith(currentWord) }
                if (completions.size == 1) {
                    val completion = completions[0]
                    // Clear the current line
                    repeat(buffer.length) { print("\b \b") }
                    buffer.clear()
                    // Add previous arguments (if any) and completed command
                    buffer.append(previousArgs + completion)
                    cursorPosition = buffer.length
                    print(buffer.toString())
                    // Add space after command if it wasn't there
                    if (!currentInput.endsWith(" ")) {
                        buffer.append(" ")
                        cursorPosition++
                        print(" ")
                    }
                    System.out.flush()
                }
            }
            // For arguments after the command, we won't implement specific completion yet
            // but allow the user to continue typing
        }
    }

    private fun executeCommand(command: Command, pathCommands: Map<String, String>, redirects: Redirections) {
        fun createFileWithDirs(path: String, append: Boolean): FileOutputStream? {
            return try {
                val file = File(path)
                val parentDir = file.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }
                FileOutputStream(file, append)
            } catch (e: Exception) {
                System.err.println("Failed to create file: $path. Error: ${e.message}")
                null
            }
        }

        val stdoutStream = redirects.stdout?.let { createFileWithDirs(it, redirects.stdoutAppend) }
        val stderrStream = redirects.stderr?.let { createFileWithDirs(it, redirects.stderrAppend) }

        try {
            when (command) {
                is Command.Exit -> { }
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
                    ProcessBuilder("/bin/sh", "-c", "stty sane").inheritIO().start().waitFor()
                    commandExecutor.execute(command.args, currentDirectory.toFile(), env, stdoutStream, stderrStream)
                    ProcessBuilder("/bin/sh", "-c", "stty raw -echo").inheritIO().start().waitFor()
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
            pid.toInt()
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
            else -> if (pathCommands.containsKey(args[0])) Command.ExternalCommand(args)
            else Command.Unknown(args[0])
        }
        return Pair(command, redirections)
    }

    private fun parseRedirections(input: String): Pair<String, Redirections> {
        var commandPart = input
        var stdout: String? = null
        var stderr: String? = null
        var stdoutAppend = false
        var stderrAppend = false
        val pattern = "\\s*((?:1)?>>|(?:1)?>|2>>|2>)\\s*([^\\s>]+)".toRegex()
        val matches = pattern.findAll(input)
        for (match in matches) {
            val (operator, file) = match.destructured
            when (operator.trim()) {
                "2>>" -> {
                    stderr = file.trim()
                    stderrAppend = true
                }
                "2>" -> {
                    stderr = file.trim()
                    stderrAppend = false
                }
                ">>", "1>>" -> {
                    stdout = file.trim()
                    stdoutAppend = true
                }
                ">", "1>" -> {
                    stdout = file.trim()
                    stdoutAppend = false
                }
            }
            commandPart = commandPart.replace(match.value, "")
        }
        return Pair(commandPart.trim(), Redirections(stdout, stderr, stdoutAppend, stderrAppend))
    }

    // New implementation of parseArguments supporting proper quoting.
    private fun parseArguments(input: String): List<String> {
        val args = mutableListOf<String>()
        val currentArg = StringBuilder()
        var i = 0
        while (i < input.length) {
            when (val c = input[i]) {
                ' ', '\t', '\n', '\r' -> {
                    if (currentArg.isNotEmpty()) {
                        args.add(currentArg.toString())
                        currentArg.clear()
                    }
                    i++
                }
                SINGLE_QUOTE -> {
                    // Single-quoted: take everything literally (backslashes remain)
                    i++ // skip opening '
                    while (i < input.length && input[i] != SINGLE_QUOTE) {
                        currentArg.append(input[i])
                        i++
                    }
                    if (i < input.length && input[i] == SINGLE_QUOTE) {
                        i++ // skip closing '
                    }
                }
                DOUBLE_QUOTE -> {
                    // Double-quoted: backslash escapes ", \, $, and `
                    i++ // skip opening "
                    while (i < input.length && input[i] != DOUBLE_QUOTE) {
                        if (input[i] == BACKSLASH && i + 1 < input.length) {
                            val next = input[i + 1]
                            if (next == DOUBLE_QUOTE || next == BACKSLASH || next == '$' || next == '`') {
                                currentArg.append(next)
                                i += 2
                            } else {
                                currentArg.append(BACKSLASH)
                                i++
                            }
                        } else {
                            currentArg.append(input[i])
                            i++
                        }
                    }
                    if (i < input.length && input[i] == DOUBLE_QUOTE) {
                        i++ // skip closing "
                    }
                }
                BACKSLASH -> {
                    // Outside quotes: backslash escapes next character.
                    if (i + 1 < input.length) {
                        currentArg.append(input[i + 1])
                        i += 2
                    } else {
                        i++
                    }
                }
                else -> {
                    currentArg.append(c)
                    i++
                }
            }
        }
        if (currentArg.isNotEmpty()) {
            args.add(currentArg.toString())
        }
        return args
    }
}
