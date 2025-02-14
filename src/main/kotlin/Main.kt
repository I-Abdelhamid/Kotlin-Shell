fun main() {
    val builtins = setOf("echo", "exit", "type")

    while (true) {
        print("$ ")
        val command = readlnOrNull() ?: break // Read input, exit on EOF

        when {
            command == "exit 0" -> return // Exit with status 0
            command.startsWith("echo ") -> println(command.removePrefix("echo "))
            command.startsWith("type ") -> {
                val target = command.removePrefix("type ")
                if (builtins.contains(target)) {
                    println("$target is a shell builtin")
                } else {
                    println("$target: not found")
                }
            }
            else -> println("$command: command not found")
        }
    }
}
