import java.io.File
import java.nio.file.Paths

fun main() {
    val builtins = setOf("echo", "exit", "type", "pwd")
    val paths = System.getenv("PATH")?.split(":") ?: emptyList() // Get directories from PATH

    while (true) {
        print("$ ")
        val input = readlnOrNull() ?: break // Read input, exit on EOF
        val tokens = input.split(" ").filter { it.isNotEmpty() } // Tokenize input
        if (tokens.isEmpty()) continue

        val command = tokens[0]
        val args = tokens.drop(1)

        when {
            command == "exit" && args == listOf("0") -> return // Exit with status 0
            command == "echo" -> println(args.joinToString(" ")) // Print echo arguments
            command == "pwd" -> println(Paths.get("").toAbsolutePath()) // Handle pwd command
            command == "type" -> handleTypeCommand(args, builtins, paths)
            else -> executeCommand(command, args, paths)
        }
    }
}

fun handleTypeCommand(args: List<String>, builtins: Set<String>, paths: List<String>) {
    if (args.isEmpty()) return
    val target = args[0]
    when {
        builtins.contains(target) -> println("$target is a shell builtin")
        findExecutable(target, paths) != null -> println("$target is ${findExecutable(target, paths)}")
        else -> println("$target: not found")
    }
}

fun executeCommand(command: String, args: List<String>, paths: List<String>) {
    val process = try {
        ProcessBuilder(listOf(command) + args)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    } catch (e: Exception) {
        println("$command: command not found")
        return
    }
    process.waitFor() // Wait for process completion
}

fun findExecutable(command: String, paths: List<String>): String? {
    return paths.map { File(it, command) }
        .firstOrNull { it.exists() && it.canExecute() }
        ?.absolutePath
}
