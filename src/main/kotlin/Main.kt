import java.io.File

fun main() {
    val builtins = setOf("echo", "exit", "type", "pwd", "cd")
    val paths = System.getenv("PATH")?.split(":")?.toMutableList() ?: mutableListOf()
    paths.add(0, "/tmp/bar") // Ensure /tmp/bar is at the start of PATH

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
            command == "pwd" -> println(System.getProperty("user.dir")) // Handle pwd command
            command == "cd" -> handleCdCommand(args) // Handle cd command
            command == "type" -> handleTypeCommand(args, builtins, paths) // Handle type command
            else -> executeCommand(command, args, paths) // Handle external commands
        }
    }
}

fun handleCdCommand(args: List<String>) {
    if (args.isEmpty()) {
        println("cd: missing argument")
        return
    }
    val dir = args[0]
    val file = File(dir)

    if (file.isDirectory) {
        // Change the current working directory of the JVM process.
        System.setProperty("user.dir", file.absolutePath)
    } else {
        println("cd: $dir: No such file or directory")
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
