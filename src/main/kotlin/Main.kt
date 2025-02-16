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
            // Reload path commands before parsing to capture any PATH changes
            val pathCommands = PathCommandsLoader.load()
            val (parsedCommand, outputRedirect) = inputParser.parse(inputLine, pathCommands)
            executeCommand(parsedCommand, pathCommands, outputRedirect)
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }

        return true
    }

    private fun executeCommand(command: Command, pathCommands: Map<String, String>, outputRedirect: String?) {
        val outputStream = if (outputRedirect != null) {
            try {
                // Create parent directories if they don't exist
                val file = File(outputRedirect)
                file.parentFile?.mkdirs()
                FileOutputStream(file)
            } catch (e: Exception) {
                println("Error creating output file: ${e.message}")
                null
            }
        } else null

        try {
            when (command) {
                is Command.Exit -> { /* Do nothing, will exit in next loop iteration */ }
                is Command.Cd -> changeDirectory(command.directory)
                is Command.Pwd -> {
                    val output = currentDirectory.toString()
                    if (outputStream != null) {
                        outputStream.write(output.toByteArray())
                        outputStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
                is Command.Type -> {
                    val output = handleTypeCommand(command.argument)
                    if (outputStream != null) {
                        outputStream.write(output.toByteArray())
                        outputStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
                is Command.Echo -> {
                    val output = command.text
                    if (outputStream != null) {
                        outputStream.write(output.toByteArray())
                        outputStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
                is Command.ExternalCommand -> executeExternalCommand(command.args, outputStream)
                is Command.Unknown -> {
                    val output = "${command.input}: command not found"
                    if (outputStream != null) {
                        outputStream.write(output.toByteArray())
                        outputStream.write("\n".toByteArray())
                    } else {
                        println(output)
                    }
                }
            }
        } finally {
            outputStream?.close()
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

        // Always reload path commands to get the most current PATH
        val pathCommands = PathCommandsLoader.load()
        val pathCommand = pathCommands[argument]
        return pathCommand?.let { "$argument is $it" } ?: "$argument: not found"
    }

    private fun isBuiltInCommand(command: String): Boolean {
        return BuiltInCommands.entries.any { it.name.equals(command, ignoreCase = true) }
    }

    private fun executeExternalCommand(args: List<String>, outputStream: OutputStream?) {
        commandExecutor.execute(args, outputStream)
    }

    // Inner class that handles command execution
    private class CommandExecutor {
        fun execute(command: List<String>, outputStream: OutputStream?) {
            try {
                val processBuilder = ProcessBuilder(command)

                if (outputStream != null) {
                    // Redirect stdout to the file but keep stderr to console
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
                    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                } else {
                    // Redirect both stdout and stderr to console
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                }

                val process = processBuilder.start()

                if (outputStream != null) {
                    // Copy process output to the file
                    process.inputStream.copyTo(outputStream)
                }

                process.waitFor()

            } catch (e: Exception) {
                println("Error running command: ${command.firstOrNull()}")
            }
        }
    }

    // Inner class for parsing input
    private inner class InputParser {
        private val SINGLE_QUOTE = '\''
        private val DOUBLE_QUOTE = '"'
        private val BACKSLASH = '\\'

        fun parse(input: String, pathCommands: Map<String, String>): Pair<Command, String?> {
            // Check for output redirection
            val (commandPart, outputRedirect) = parseRedirection(input)

            val args = parseArguments(commandPart)
            if (args.isEmpty()) return Pair(Command.Unknown(commandPart), outputRedirect)

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

            return Pair(command, outputRedirect)
        }

        private fun parseRedirection(input: String): Pair<String, String?> {
            var commandPart = input
            var outputFile: String? = null
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
                    if (c == '>' || (c == '1' && i + 1 < input.length && input[i+1] == '>')) {
                        val redirectIndex = if (c == '>') i else i + 1
                        commandPart = input.substring(0, if (c == '1') i else redirectIndex).trim()

                        // Find the file part after '>' or '1>'
                        val fileStart = if (c == '1') redirectIndex + 1 else redirectIndex + 1
                        if (fileStart < input.length) {
                            outputFile = parseRedirectionTarget(input.substring(fileStart).trim())
                        }
                        break
                    }
                }

                i++
            }

            return Pair(commandPart, outputFile)
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
                            // Handle escaped characters in double quotes
                            val nextChar = input[i + 1]
                            if (nextChar == BACKSLASH || nextChar == DOUBLE_QUOTE ||
                                nextChar == '$' || nextChar == '\n') {
                                sb.append(nextChar)
                                i += 2  // Skip both backslash and the escaped character
                            } else {
                                // Backslash is treated literally if not escaping \, ", $, or newline
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
                                // Outside quotes, backslash escapes any character
                                sb.append(input[i + 1])
                                i += 2  // Skip both backslash and the escaped character
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

// Sealed class hierarchy for commands
sealed class Command {
    object Exit : Command()
    data class Cd(val directory: String?) : Command()
    object Pwd : Command()
    data class Type(val argument: String?) : Command()
    data class Echo(val text: String) : Command()
    data class ExternalCommand(val args: List<String>) : Command()
    data class Unknown(val input: String) : Command()
}

// Enum for built-in commands
enum class BuiltInCommands {
    EXIT, ECHO, TYPE, PWD, CD
}

// Object responsible for loading path commands
object PathCommandsLoader {
    fun load(): Map<String, String> {
        val path = System.getenv()["PATH"] ?: return emptyMap()
        val commands = mutableMapOf<String, String>()

        path.split(":")
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
            .forEach { dir ->
                dir.listFiles()
                    ?.filter { it.isFile && it.canExecute() }
                    ?.forEach { file ->
                        commands.putIfAbsent(file.name, file.absolutePath)
                    }
            }

        return commands
    }
}
