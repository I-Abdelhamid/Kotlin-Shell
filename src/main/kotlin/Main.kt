import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedList
import kotlin.io.path.isExecutable
import kotlin.io.path.notExists
import kotlin.system.exitProcess

// Command Interface
interface Command {
    fun execute(args: List<String>)
}

// Command for echo functionality
class EchoCommand : Command {
    override fun execute(args: List<String>) {
        println(args.joinToString(" "))
    }
}

// Command for pwd functionality
class PwdCommand : Command {
    override fun execute(args: List<String>) {
        println(System.getProperty("user.dir"))
    }
}

// Command for cd functionality
class CdCommand(private val directoryContext: DirectoryContext) : Command {
    override fun execute(args: List<String>) {
        if (args.isEmpty()) {
            println("cd: missing argument")
            return
        }

        val targetPath = resolvePath(args[0])
        if (targetPath.notExists()) {
            println("cd: ${args[0]}: No such file or directory")
        } else {
            directoryContext.changeDirectory(targetPath)
        }
    }

    private fun resolvePath(path: String): Path {
        val home = System.getenv("HOME")
        val dirResolved = path.replace("~", home)
        return if (dirResolved[0] == '/') {
            Paths.get(dirResolved)
        } else {
            Paths.get(System.getProperty("user.dir"), dirResolved).toAbsolutePath().normalize()
        }
    }
}

// Command for exit functionality
class ExitCommand : Command {
    override fun execute(args: List<String>) {
        exitProcess(0)
    }
}

// Command for type functionality
class TypeCommand(private val path: ShellPath) : Command {
    private val builtins = setOf("echo", "pwd", "cd", "exit", "type")

    override fun execute(args: List<String>) {
        if (args.isNotEmpty()) {
            val cmd = args[0]
            if (cmd in builtins) {
                // The command is a shell builtin
                println("$cmd is a shell builtin")
            } else {
                // The command is an external command
                val executable = findExecutable(path, cmd)
                if (executable != null) {
                    println("$cmd is $executable")
                } else {
                    println("$cmd: not found")
                }
            }
        }
    }

    private fun findExecutable(path: ShellPath, cmd: String): Path? {
        for (dir in path) {
            try {
                val p = Paths.get(dir, cmd)
                if (p.isExecutable()) {
                    return p
                }
            } catch (_: java.nio.file.NoSuchFileException) {}
        }
        return null
    }
}


// Command Registry to register and execute commands
class CommandRegistry(private val path: ShellPath, private val directoryContext: DirectoryContext) {
    private val commands = mutableMapOf<String, Command>()

    init {
        commands["echo"] = EchoCommand()
        commands["pwd"] = PwdCommand()
        commands["cd"] = CdCommand(directoryContext)
        commands["exit"] = ExitCommand()
        commands["type"] = TypeCommand(path)
    }

    fun executeCommand(command: String, args: List<String>) {
        val cmd = commands[command]
        if (cmd != null) {
            cmd.execute(args)
        } else {
            // Handle external commands like 'cat'
            executeExternalCommand(command, args)
        }
    }

    private fun executeExternalCommand(command: String, args: List<String>) {
        val executable = findExecutable(path, command)
        if (executable != null) {
            val executableName = executable.fileName.toString()  // Extract only the command name
            val process = ProcessBuilder(executableName, *args.toTypedArray())
                .directory(File(System.getProperty("user.dir")))
                .inheritIO()
                .start()
            process.waitFor()
        } else {
            println("$command: command not found")
        }
    }


    private fun findExecutable(path: ShellPath, cmd: String): Path? {
        for (dir in path) {
            try {
                val p = Paths.get(dir, cmd)
                if (p.isExecutable()) {
                    return p
                }
            } catch (_: java.nio.file.NoSuchFileException) {}
        }
        return null
    }
}

// Directory Context for managing the current directory state
class DirectoryContext {
    var currentDirectory: File = File(System.getProperty("user.dir"))

    fun changeDirectory(newDir: Path) {
        currentDirectory = newDir.toFile()
        System.setProperty("user.dir", newDir.toString())
    }
}

typealias ShellPath = List<String>

fun main() {
    val path = System.getenv("PATH").split(":")
    val directoryContext = DirectoryContext()
    val commandRegistry = CommandRegistry(path, directoryContext)

    while (true) {
        prompt()
        val input = readln() // Wait for user input
        val inputs = splitShellWords(input)
        if (inputs.isNotEmpty()) {
            commandRegistry.executeCommand(inputs[0], inputs.drop(1))
        }
    }
}

fun prompt() {
    print("$ ")
}

fun splitShellWords(input: String): List<String> {
    val matches = WORDS_REGEX.findAll(input)
    val out = LinkedList<String>()
    var acc = StringBuilder()
    for (matchResult in matches) {
        val (_, word, sq, dq, sep) = matchResult.groups.toList();
        acc.append((word ?: sq ?: dq)!!.value)
        if (sep != null) {
            out.add(acc.toString())
            acc = StringBuilder()
        }
    }
    return out
}

const val START_REGEX = """\G\s*"""
const val UNQUOTED_REGEX = """([^\s\\\'\"]+)"""
const val SINGLE_QUOTED_REGEX = """'([^\']*)'"""
const val DOUBLE_QUOTED_REGEX = """"((?:[^\"\\]|\\)*)""""
const val END_REGEX = """(\s|\z)?"""
val WORDS_REGEX = "$START_REGEX(?:$UNQUOTED_REGEX|$SINGLE_QUOTED_REGEX|$DOUBLE_QUOTED_REGEX)$END_REGEX".toRegex()
