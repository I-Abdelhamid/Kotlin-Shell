import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
            val parsedCommand = inputParser.parse(inputLine, pathCommands)
            executeCommand(parsedCommand, pathCommands)
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }

        return true
    }

    private fun executeCommand(command: Command, pathCommands: Map<String, String>) {
        when (command) {
            is Command.Exit -> { /* Do nothing, will exit in next loop iteration */ }
            is Command.Cd -> changeDirectory(command.directory)
            is Command.Pwd -> println(currentDirectory)
            is Command.Type -> println(handleTypeCommand(command.argument, pathCommands))
            is Command.Echo -> println(command.text)
            is Command.ExternalCommand -> executeExternalCommand(command.args)
            is Command.Unknown -> println("${command.input}: command not found")
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

    private fun handleTypeCommand(argument: String?, pathCommands: Map<String, String>): String {
        if (argument == null) return "type: missing argument"

        if (isBuiltInCommand(argument)) return "$argument is a shell builtin"

        val pathCommand = pathCommands[argument]
        return pathCommand?.let { "$argument is $it" } ?: "$argument: not found"
    }

    private fun isBuiltInCommand(command: String): Boolean {
        return BuiltInCommands.entries.any { it.name.equals(command, ignoreCase = true) }
    }

    private fun executeExternalCommand(args: List<String>) {
        commandExecutor.execute(args)
    }

    // Inner class that handles command execution
    private class CommandExecutor {
        fun execute(command: List<String>) {
            try {
                ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor()
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

        fun parse(input: String, pathCommands: Map<String, String>): Command {
            val args = parseArguments(input)
            if (args.isEmpty()) return Command.Unknown(input)

            val firstArg = args.first()
            return when {
                firstArg == "exit" && args.size > 1 && args[1] == "0" -> Command.Exit
                firstArg == "cd" -> Command.Cd(args.getOrNull(1))
                firstArg == "pwd" -> Command.Pwd
                firstArg == "type" -> Command.Type(args.getOrNull(1))
                firstArg == "echo" -> Command.Echo(args.drop(1).joinToString(" "))
                pathCommands.containsKey(firstArg) -> Command.ExternalCommand(args)
                else -> Command.Unknown(input)
            }
        }

        private fun parseArguments(input: String): List<String> {
            val args = mutableListOf<String>()
            val sb = StringBuilder()
            var currentQuote: Char? = null
            var useLiteral = false

            for (c in input) {
                if (useLiteral) {
                    sb.append(c)
                    useLiteral = false
                    continue
                }

                when (currentQuote) {
                    SINGLE_QUOTE -> {
                        if (c == SINGLE_QUOTE) {
                            currentQuote = null
                        } else {
                            sb.append(c)
                        }
                    }
                    DOUBLE_QUOTE -> {
                        if (c == DOUBLE_QUOTE) {
                            currentQuote = null
                        } else {
                            sb.append(c)
                        }
                    }
                    else -> {
                        when {
                            c.isWhitespace() -> {
                                if (sb.isNotBlank()) {
                                    args.add(sb.toString())
                                    sb.clear()
                                }
                            }
                            c == BACKSLASH -> useLiteral = true
                            c == SINGLE_QUOTE -> currentQuote = SINGLE_QUOTE
                            c == DOUBLE_QUOTE -> currentQuote = DOUBLE_QUOTE
                            else -> {
                                sb.append(c)
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
