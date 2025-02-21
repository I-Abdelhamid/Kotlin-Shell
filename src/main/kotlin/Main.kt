import com.shell.Shell

fun main() {
    ProcessBuilder("/bin/sh", "-c", "stty raw -echo").inheritIO().start().waitFor()
    try {
        val shell = Shell()
        shell.start()
    } finally {
        ProcessBuilder("/bin/sh", "-c", "stty sane").inheritIO().start().waitFor()
    }
}
