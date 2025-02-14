fun main() {
    while (true) {
        print("$ ")
        val command = readlnOrNull() ?: break // Read input, exit on EOF

        if (command == "exit 0") {
            return // Exit with status 0
        }

        println("$command: command not found")
    }
}
