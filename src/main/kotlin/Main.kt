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

// Command for 'pwd' functionality
class PwdCommand(private val directoryContext: DirectoryContext) : Command {
    override fun execute(args: List<String>) {
        println(directoryContext.currentDirectory.absolutePath)
    }
}

// Command for 'echo' functionality
class EchoCommand : Command {
    override fun execute(args: List<String>) {
        println(args.joinToString(" "))
    }
}

// Command for 'exit' functionality
class ExitCommand : Command {
    override fun execute(args: List<String>) {
        if (args == listOf("0")) {
            println("Exiting...")
            System.exit(0)
        } else {
            println("exit: Invalid arguments")
        }
    }
}

// Command for 'type' functionality
class TypeCommand(private val builtins: Set<String>, private val paths: List<String>) : Command {
    override fun execute(args: List<String>) {
        if (args.isEmpty()) return

        val target = args[0]
        when {
            builtins.contains(target) -> println("$target is a shell builtin")
            findExecutable(target, paths) != null -> println("$target is ${findExecutable(target, paths)}")
            else -> println("$target: not found")
        }
    }

    private fun findExecutable(command: String, paths: List<String>): String? {
        return paths.map { File(it, command) }
            .firstOrNull { it.exists() && it.canExecute() }
            ?.absolutePath
    }
}

// Command Registry to register and execute commands
class CommandRegistry(private val builtins: Set<String>, private val paths: List<String>, private val directoryContext: DirectoryContext) {

    private val commands = mutableMapOf<String, Command>()

    init {
        // Register all commands
        commands["cd"] = CdCommand(directoryContext)
        commands["pwd"] = PwdCommand(directoryContext)
        commands["echo"] = EchoCommand()
        commands["exit"] = ExitCommand()
        commands["type"] = TypeCommand(builtins, paths)
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
    val builtins = setOf("echo", "exit", "type", "pwd", "cd")
    val paths = System.getenv("PATH")?.split(":")?.toMutableList() ?: mutableListOf()
    val directoryContext = DirectoryContext() // To manage the current directory state
    val commandRegistry = CommandRegistry(builtins, paths, directoryContext)

    while (true) {
        print("$ ")
        val input = readlnOrNull() ?: break // Read input, exit on EOF
        val tokens = input.split(" ").filter { it.isNotEmpty() } // Tokenize input
        if (tokens.isEmpty()) continue

        val command = tokens[0]
        val args = tokens.drop(1)

        commandRegistry.executeCommand(command, args) // Execute command using the registry
    }
}
