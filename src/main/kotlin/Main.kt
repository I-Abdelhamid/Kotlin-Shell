import java.io.File

fun main() {
    val builtins = setOf("echo", "exit", "type")
    val paths = System.getenv("PATH")?.split(":") ?: emptyList() // Get directories from PATH

    while (true) {
        print("$ ")
        val command = readlnOrNull() ?: break // Read input, exit on EOF

        when {
            command == "exit 0" -> return // Exit with status 0
            command.startsWith("echo ") -> println(command.removePrefix("echo "))
            command.startsWith("type ") -> {
                val target = command.removePrefix("type ")
                when {
                    builtins.contains(target) -> println("$target is a shell builtin")
                    findExecutable(target, paths) != null -> println("$target is ${findExecutable(target, paths)}")
                    else -> println("$target: not found")
                }
            }
            else -> println("$command: command not found")
        }
    }
}

fun findExecutable(command: String, paths: List<String>): String? {
    return paths.map { File(it, command) }
        .firstOrNull { it.exists() && it.canExecute() }
        ?.absolutePath
}
