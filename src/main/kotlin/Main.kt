fun main() {
    while (true) {
        print("$ ")
        val command = readlnOrNull() ?: break // Read input, exit on EOF

        when {
            command == "exit 0" -> return // Exit with status 0
            command.startsWith("echo ") -> println(command.removePrefix("echo "))
            else -> println("$command: command not found")
        }
    }
}
