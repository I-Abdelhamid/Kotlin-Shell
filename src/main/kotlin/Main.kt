import java.io.File

fun main() {
    val builtins = setOf("echo", "exit", "type")
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
    val executable = findExecutable(command, paths)
    if (executable != null) {
        try {
            val process = ProcessBuilder(listOf(executable) + args)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
            process.waitFor() // Wait for process to complete
        } catch (e: Exception) {
            println("Failed to execute $command")
        }
    } else {
        println("$command: command not found")
    }
}

fun findExecutable(command: String, paths: List<String>): String? {
    return paths.map { File(it, command) }
        .firstOrNull { it.exists() && it.canExecute() }
        ?.absolutePath
}
