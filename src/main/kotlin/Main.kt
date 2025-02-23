import com.shell.Shell

fun main() {
    val isTestEnv = System.getenv("TEST_ENV") == "true"
    if (!isTestEnv && runCommand("/bin/sh", "-c", "stty raw -echo")) {
        try {
            val shell = Shell()
            shell.start()
        } finally {
            runCommand("/bin/sh", "-c", "stty sane")
        }
    } else {
        val shell = Shell()
        shell.start()
    }
}

private fun runCommand(vararg command: String): Boolean {
    return try {
        ProcessBuilder(*command).inheritIO().start().waitFor() == 0
    } catch (e: Exception) {
        false
    }
}
