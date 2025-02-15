import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private var pathCommands: HashMap<String, String> = hashMapOf()
private var currentDir = Paths.get("").toAbsolutePath()

fun main() {
    pathCommands = getPathCommands()
    val commandDispatcher = CommandDispatcher(pathCommands)

    do {
        val continuePrompt = prompt(commandDispatcher)
    } while (continuePrompt)
}

fun String.getPathCommandOrNull(): String? {
    return pathCommands[this]
}

private const val EXIT_COMMAND = "exit 0"
private const val ECHO_COMMAND = "echo"
private const val TYPE_COMMAND = "type"
private const val PWD_COMMAND = "pwd"
private const val CD_COMMAND = "cd"

private fun prompt(commandDispatcher: CommandDispatcher): Boolean {
    print("$ ")
    val command = readln()
    if (EXIT_COMMAND == command) return false

    val args = parseCommandArguments(command)
    val firstCommand = args.firstOrNull()

    firstCommand?.let {
        commandDispatcher.dispatch(it, args)
    } ?: println("$command: command not found")

    return true
}

fun parseCommandArguments(command: String): List<String> {
    val sb = StringBuilder()
    val args = mutableListOf<String>()
    var current: Char? = null
    command.toCharArray().forEach { c ->
        when (current) {
            '\'' -> if (c == '\'') current = null else sb.append(c)
            '\"' -> if (c == '\"') current = null else sb.append(c)
            else -> when {
                c.isWhitespace() -> {
                    if (sb.isNotBlank()) {
                        args.add(sb.toString())
                        sb.clear()
                    }
                }
                c == '\'' -> current = '\''
                c == '\"' -> current = '\"'
                else -> sb.append(c)
            }
        }
    }
    if (sb.isNotBlank()) args.add(sb.toString())
    return args
}

class CommandDispatcher(private val pathCommands: HashMap<String, String>) {

    private val builtInCommands = mapOf(
        ECHO_COMMAND to ::handleEchoCommand,
        TYPE_COMMAND to ::handleTypeCommand,
        PWD_COMMAND to ::handlePwdCommand,
        CD_COMMAND to ::handleCdCommand
    )

    fun dispatch(command: String, args: List<String>) {
        builtInCommands[command]?.invoke(args) ?: run {
            pathCommands[command]?.let { runExternalCommand(command, args) }
                ?: println("$command: command not found")
        }
    }

    private fun handleEchoCommand(args: List<String>) {
        println(args.drop(1).joinToString(" ").trim())
    }

    private fun handleTypeCommand(args: List<String>) {
        if (args.size >= 2) {
            val command = args[1]
            if (command.isBuiltInCommand()) {
                println("$command is a shell builtin")
            } else {
                pathCommands[command]?.let {
                    println("$command is $it")
                } ?: println("$command: not found")
            }
        }
    }

    private fun handlePwdCommand(args: List<String>) {
        println(currentDir)
    }

    private fun handleCdCommand(args: List<String>) {
        val dir = args.getOrNull(1)
        dir?.let { changeDirectory(it) }
    }

    private fun runExternalCommand(command: String, args: List<String>) {
        try {
            ProcessBuilder(args)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor()
        } catch (e: Exception) {
            println("Error running command: $command")
        }
    }

    private fun changeDirectory(dir: String) {
        val newPath = when (dir) {
            "~" -> Paths.get(System.getenv("HOME"))
            else -> {
                val resolvedPath = if (Paths.get(dir).isAbsolute) Paths.get(dir)
                else currentDir.resolve(dir).normalize()
                resolvedPath
            }
        }
        if (Files.exists(newPath)) {
            currentDir = newPath
        } else {
            println("cd: $newPath: No such file or directory")
        }
    }

    private fun String.isBuiltInCommand(): Boolean {
        return BuiltInCommands.entries.any { it.name == this.uppercase() }
    }
}

enum class BuiltInCommands {
    EXIT,
    ECHO,
    TYPE,
    PWD,
    CD
}

private fun getPathCommands(): HashMap<String, String> {
    val path = System.getenv()["PATH"]
    val commands = hashMapOf<String, String>()
    path?.split(":")
        ?.map { File(it) }
        ?.filter { it.exists() && it.isDirectory }
        ?.forEach { dir ->
            dir.listFiles()
                ?.filter { it.isFile && it.canExecute() }
                ?.forEach { file -> commands.putIfAbsent(file.name, file.absolutePath) }
        }
    return commands
}
