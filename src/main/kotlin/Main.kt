import java.io.File

// Command Interface
interface Command {
    fun execute(args: List<String>)
}

// Directory Context for managing the current directory state
class DirectoryContext {
    var currentDirectory: File = File(System.getProperty("user.dir"))

    fun changeDirectory(newDir: File) {
        if (newDir.exists() && newDir.isDirectory) {
            currentDirectory = newDir
        } else {
            throw IllegalArgumentException("cd: ${newDir.absolutePath}: No such file or directory")
        }
    }
}

// Command for 'echo' functionality
class EchoCommand : Command {
    override fun execute(args: List<String>) {
        val result = buildString {
            var prevWasQuoted = false

            for (arg in args) {
                val isQuoted = arg.startsWith("'") && arg.endsWith("'")
                val cleanArg = arg.removeSurrounding("'")

                // Add a space if the previous element was quoted OR it's a new separate argument
                if (isNotEmpty() && (!prevWasQuoted || !isQuoted)) append(" ")

                append(cleanArg)
                prevWasQuoted = isQuoted
            }
        }
        println(result)
    }
}

// Command for 'pwd' functionality
class PwdCommand(private val directoryContext: DirectoryContext) : Command {
    override fun execute(args: List<String>) {
        println(directoryContext.currentDirectory.absolutePath)
    }
}

// Command for 'cd' functionality
class CdCommand(private val directoryContext: DirectoryContext) : Command {
    override fun execute(args: List<String>) {
        if (args.isEmpty()) {
            println("cd: missing argument")
            return
        }

        val targetPath = resolvePath(args[0])
        try {
            directoryContext.changeDirectory(targetPath)
        } catch (e: IllegalArgumentException) {
            println(e.message)
        }
    }

    private fun resolvePath(path: String): File {
        return when {
            path == "~" -> File(System.getenv("HOME") ?: throw IllegalStateException("HOME environment variable is not set"))
            path.startsWith("/") -> File(path) // Absolute path
            else -> File(directoryContext.currentDirectory, path) // Relative path
        }
    }
}

// Command for 'cat' functionality
class CatCommand : Command {
    override fun execute(args: List<String>) {
        val processedArgs = args.map { it.removeSurrounding("'") }
        processedArgs.forEach {
            if (it.isEmpty()) {
                println("cat: No file specified")
            } else {
                val file = File(it)
                if (file.exists() && file.isFile) {
                    println(file.readText())  // Read file content
                } else {
                    println("cat: $it: No such file")
                }
            }
        }
    }
}

// Command Registry to register and execute commands
class CommandRegistry(private val directoryContext: DirectoryContext) {
    private val commands = mutableMapOf<String, Command>()

    init {
        commands["cd"] = CdCommand(directoryContext)
        commands["pwd"] = PwdCommand(directoryContext)
        commands["echo"] = EchoCommand()
        commands["cat"] = CatCommand()
    }

    fun executeCommand(command: String, args: List<String>) {
        val cmd = commands[command]
        if (cmd != null) {
            cmd.execute(args)
        } else {
            println("$command: command not found")
        }
    }
}

// Main Shell Program
fun main() {
    val directoryContext = DirectoryContext()
    val commandRegistry = CommandRegistry(directoryContext)

    while (true) {
        print("$ ")
        val input = readlnOrNull() ?: break
        val tokens = parseInput(input)
        if (tokens.isEmpty()) continue

        val command = tokens[0]
        val args = tokens.drop(1)

        commandRegistry.executeCommand(command, args)
    }
}

// Function to parse input and handle single quotes properly
fun parseInput(input: String): List<String> {
    val regex = """'[^']*'|\S+""".toRegex() // Matches quoted strings and non-whitespace words
    return regex.findAll(input).map { it.value }.toList()
}
