import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.io.OutputStream

// Main application entry point
fun main() {
    val shell = Shell()
    shell.start()
}

// Shell class that encapsulates all functionality
class Shell {
    private val commandExecutor = CommandExecutor()
    private val inputParser = InputParser()
    private var currentDirectory = Paths.get("").toAbsolutePath()

    fun start() {
        do {
            val shouldContinue = promptAndExecute()
        } while (shouldContinue)
    }

    private fun promptAndExecute(): Boolean {
        print("$ ")
        val inputLine = readln()

        if (inputLine == "exit 0") return false

        try {
            val pathCommands = PathCommandsLoader.load()
            val (parsedCommand, redirects) = inputParser.parse(inputLine, pathCommands)
            executeCommand(parsedCommand, pathCommands, redirects)
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }

        return true
    }

    private fun executeCommand(command: Command, pathCommands: Map<String, String>, redirects: Redirections) {
        val stdoutStream = if (redirects.stdout != null) {
            try {
                val file = File(redirects.stdout)
                file.parentFile?.mkdirs()
                FileOutputStream(file)
            } catch (e: Exception) {
                println("Error creating output file: ${e.message}")
                null
            }
        } else null

        val stderrStream = if (redirects.stderr != null) {
            try {
                val file = File(redirects.stderr)
                file.parentFile?.mkdirs()
                FileOutputStream(file)
            } catch (e: Exception) {
                println("Error creating error file: ${e.message}")
                null
            }
        } else null

        try {
            when (command) {
                is Command.Exit -> { /* Do nothing, will exit in next loop iteration */ }
                is Command.Cd -> changeDirectory(command.directory)
                is Command.Pwd -> {
                    val output = currentDirectory.toString()
                    if (stdoutStream != null) {
                        stdoutStream.write(output.toByteArray())
                        stdoutStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
                is Command.Type -> {
                    val output = handleTypeCommand(command.argument)
                    if (stdoutStream != null) {
                        stdoutStream.write(output.toByteArray())
                        stdoutStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
                is Command.Echo -> {
                    val output = command.text
                    if (stdoutStream != null) {
                        stdoutStream.write(output.toByteArray())
                        stdoutStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
                is Command.ExternalCommand -> executeExternalCommand(command.args, stdoutStream, stderrStream)
                is Command.Unknown -> {
                    val output = "${command.input}: command not found"
                    if (stdoutStream != null) {
                        stdoutStream.write(output.toByteArray())
                        stdoutStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
            }
        } finally {
            stdoutStream?.close()
            stderrStream?.close()
        }
    }

    private fun changeDirectory(dir: String?) {
        dir?.let {
            val newPath = when {
                dir == "~" -> Paths.get(System.getenv("HOME") ?: "")
                Paths.get(dir).isAbsolute -> Paths.get(dir)
                else -> currentDirectory.resolve(Paths.get(dir)).normalize()
            }

            if (Files.exists(newPath)) {
                currentDirectory = newPath
            } else {
                println("cd: $newPath: No such file or directory")
            }
        }
    }

    private fun handleTypeCommand(argument: String?): String {
        if (argument == null) return "type: missing argument"

        if (isBuiltInCommand(argument)) return "$argument is a shell builtin"

        val pathCommands = PathCommandsLoader.load()
        val pathCommand = pathCommands[argument]
        return pathCommand?.let { "$argument is $it" } ?: "$argument: not found"
    }

    private fun isBuiltInCommand(command: String): Boolean {
        return BuiltInCommands.entries.any { it.name.equals(command, ignoreCase = true) }
    }

    private fun executeExternalCommand(args: List<String>, stdoutStream: OutputStream?, stderrStream: OutputStream?) {
        commandExecutor.execute(args, stdoutStream, stderrStream)
    }

    private class CommandExecutor {
        fun execute(command: List<String>, stdoutStream: OutputStream?, stderrStream: OutputStream?) {
            try {
                val processBuilder = ProcessBuilder(command)

                processBuilder.redirectOutput(
                    if (stdoutStream != null) ProcessBuilder.Redirect.PIPE
                    else ProcessBuilder.Redirect.INHERIT
                )
                processBuilder.redirectError(
                    if (stderrStream != null) ProcessBuilder.Redirect.PIPE
                    else ProcessBuilder.Redirect.INHERIT
                )

                val process = processBuilder.start()

                if (stdoutStream != null) {
                    process.inputStream.copyTo(stdoutStream)
                }

                if (stderrStream != null) {
                    process.errorStream.copyTo(stderrStream)
                }

                process.waitFor()

            } catch (e: Exception) {
                println("Error running command: ${command.firstOrNull()}")
            }
        }
    }

    private inner class InputParser {
        private val SINGLE_QUOTE = '\''
        private val DOUBLE_QUOTE = '"'
        private val BACKSLASH = '\\'

        fun parse(input: String, pathCommands: Map<String, String>): Pair<Command, Redirections> {
            val (commandPart, redirections) = parseRedirection(input)

            val args = parseArguments(commandPart)
            if (args.isEmpty()) return Pair(Command.Unknown(commandPart), redirections)

            val firstArg = args.first()
            val command = when {
                firstArg == "exit" && args.size > 1 && args[1] == "0" -> Command.Exit
                firstArg == "cd" -> Command.Cd(args.getOrNull(1))
                firstArg == "pwd" -> Command.Pwd
                firstArg == "type" -> Command.Type(args.getOrNull(1))
                firstArg == "echo" -> Command.Echo(args.drop(1).joinToString(" "))
                pathCommands.containsKey(firstArg) -> Command.ExternalCommand(args)
                else -> Command.Unknown(commandPart)
            }

            return Pair(command, redirections)
        }

        private fun parseRedirection(input: String): Pair<String, Redirections> {
            var commandPart = input
            var stdoutFile: String? = null
            var stderrFile: String? = null
            var inQuotes = false
            var quoteChar: Char? = null
            var i = 0

            while (i < input.length) {
                val c = input[i]

                // Handle quotes
                if ((c == SINGLE_QUOTE || c == DOUBLE_QUOTE) && (i == 0 || input[i-1] != BACKSLASH)) {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = c
                    } else if (quoteChar == c) {
                        inQuotes = false
                        quoteChar = null
                    }
                }

                // Look for redirection operators outside of quotes
                if (!inQuotes) {
                    when {
                        // Handle stdout redirection (> or 1>)
                        c == '>' || (c == '1' && i + 1 < input.length && input[i + 1] == '>') -> {
                            val redirectStart = if (c == '1') i else i
                            commandPart = input.substring(0, redirectStart).trim()
                            val fileStart = if (c == '1') i + 2 else i + 1
                            if (fileStart < input.length) {
                                stdoutFile = parseRedirectionTarget(input.substring(fileStart).trim())
                            }
                            break
                        }
                        // Handle stderr redirection (2>)
                        c == '2' && i + 1 < input.length && input[i + 1] == '>' -> {
                            commandPart = input.substring(0, i).trim()
                            if (i + 2 < input.length) {
                                stderrFile = parseRedirectionTarget(input.substring(i + 2).trim())
                            }
                            break
                        }
                    }
                }

                i++
            }

            return Pair(commandPart, Redirections(stdoutFile, stderrFile))
        }

        private fun parseRedirectionTarget(input: String): String {
            return parseArguments(input).firstOrNull() ?: ""
        }

        private fun parseArguments(input: String): List<String> {
            val args = mutableListOf<String>()
            val sb = StringBuilder()
            var currentQuote: Char? = null
            var i = 0

            while (i < input.length) {
                val c = input[i]

                when (currentQuote) {
                    SINGLE_QUOTE -> {
                        if (c == SINGLE_QUOTE) {
                            currentQuote = null
                        } else {
                            sb.append(c)
                        }
                        i++
                    }
                    DOUBLE_QUOTE -> {
                        if (c == DOUBLE_QUOTE) {
                            currentQuote = null
                            i++
                        } else if (c == BACKSLASH && i + 1 < input.length) {
                            val nextChar = input[i + 1]
                            if (nextChar == BACKSLASH || nextChar == DOUBLE_QUOTE ||
                                nextChar == '$' || nextChar == '\n') {
                                sb.append(nextChar)
                                i += 2
                            } else {
                                sb.append(BACKSLASH)
                                i++
                            }
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    else -> {
                        when {
                            c.isWhitespace() -> {
                                if (sb.isNotBlank()) {
                                    args.add(sb.toString())
                                    sb.clear()
                                }
                                i++
                            }
                            c == BACKSLASH && i + 1 < input.length -> {
                                sb.append(input[i + 1])
                                i += 2
                            }
                            c == SINGLE_QUOTE -> {
                                currentQuote = SINGLE_QUOTE
                                i++
                            }
                            c == DOUBLE_QUOTE -> {
                                currentQuote = DOUBLE_QUOTE
                                i++
                            }
                            else -> {
                                sb.append(c)
                                i++
                            }
                        }
                    }
                }
            }

            if (sb.isNotBlank()) args.add(sb.toString())
            return args
        }
    }
}

data class Redirections(
    val stdout: String? = null,
    val stderr: String? = null
)

sealed class Command {
    object Exit : Command()
    data class Cd(val directory: String?) : Command()
    object Pwd : Command()
    data class Type(val argument: String?) : Command()
    data class Echo(val text: String) : Command()
    data class ExternalCommand(val args: List<String>) : Command()
    data class Unknown(val input: String) : Command()
}

enum class BuiltInCommands {
    EXIT, ECHO, TYPE, PWD, CD
}

object PathCommandsLoader {
    fun load(): Map<String, String> {
        // Explicitly get PATH environment variable using ProcessEnvironment
        val pathValue = System.getenv("PATH") ?: return emptyMap()
        val commands = mutableMapOf<String, String>()

        // Process each directory in PATH
        pathValue.split(File.pathSeparator)
            .map { pathDir ->
                // Convert to absolute path and handle potential symlinks
                Paths.get(pathDir).toAbsolutePath().normalize().toString()
            }
            .distinct() // Remove duplicate paths
            .forEach { pathDir ->
                val directory = File(pathDir)
                if (directory.exists() && directory.isDirectory) {
                    try {
                        directory.listFiles()?.forEach { file ->
                            if (file.isFile && file.canExecute()) {
                                // Store the absolute path of the executable
                                commands.putIfAbsent(file.name, file.absolutePath)
                            }
                        }
                    } catch (e: SecurityException) {
                        // Silently ignore directories we can't access
                    }
                }
            }

        return commands
    }
}
