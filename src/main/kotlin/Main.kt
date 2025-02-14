fun main() {
    while (true) {
        print("$ ")
        val command = readlnOrNull() ?: break  // Read input, exit on EOF
        println("$command: command not found")
    }
}
