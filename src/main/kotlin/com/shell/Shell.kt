package com.shell

import com.shell.console.ConsoleReader
import com.shell.executor.CommandExecutor
import com.shell.parser.InputParser
import com.shell.util.PathCommandsLoader
import com.shell.util.Redirections
import commands.BuiltInCommands
import commands.Command
import java.nio.file.Paths
import java.nio.file.Files
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class Shell {
    private val commandExecutor = CommandExecutor()
    private val inputParser = InputParser()
    private var currentDirectory = Paths.get("").toAbsolutePath()
    private val reader = ConsoleReader()
    private val env = System.getenv().toMutableMap()

    fun start() {
        printPrompt()
        while (true) {
            val inputLine = reader.readLine().trimEnd()
            if (inputLine.isBlank()) {
                printPrompt()
                continue
            }
            if (inputLine == "exit" || inputLine == "exit 0") break

            try {
                val pathCommands = PathCommandsLoader.load(env["PATH"] ?: "")
                val (parsedCommand, redirects) = inputParser.parse(inputLine, pathCommands)
                executeCommand(parsedCommand, pathCommands, redirects)
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
            printPrompt()
        }
    }

    private fun printPrompt() {
        print("\r\u001B[K$ ")
        System.out.flush()
    }

    private fun executeCommand(command: Command, pathCommands: Map<String, String>, redirects: Redirections) {
        fun createFileWithDirs(path: String, append: Boolean): FileOutputStream? {
            return try {
                val file = File(path)
                val parentDir = file.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }
                FileOutputStream(file, append)
            } catch (e: Exception) {
                System.err.println("Failed to create file: $path. Error: ${e.message}")
                null
            }
        }

        val stdoutStream = redirects.stdout?.let { createFileWithDirs(it, redirects.stdoutAppend) }
        val stderrStream = redirects.stderr?.let { createFileWithDirs(it, redirects.stderrAppend) }

        try {
            when (command) {
                is Command.Exit -> {}
                is Command.Cd -> changeDirectory(command.directory)
                is Command.Pwd -> {
                    val output = "$currentDirectory\n"
                    stdoutStream?.write(output.toByteArray()) ?: print(output)
                }

                is Command.Type -> {
                    val output = handleTypeCommand(command.argument, pathCommands) + "\n"
                    stdoutStream?.write(output.toByteArray()) ?: print(output)
                }

                is Command.Echo -> {
                    val output = "${command.text}\n"
                    stdoutStream?.write(output.toByteArray()) ?: print(output)
                }

                is Command.Kill -> handleKillCommand(command.pid, stderrStream)
                is Command.ExternalCommand -> {
                    ProcessBuilder("/bin/sh", "-c", "stty sane").inheritIO().start().waitFor()
                    commandExecutor.execute(command.args, currentDirectory.toFile(), env, stdoutStream, stderrStream)
                    ProcessBuilder("/bin/sh", "-c", "stty raw -echo").inheritIO().start().waitFor()
                }

                is Command.Unknown -> {
                    val errorMsg = "${command.input}: command not found\n"
                    // Apply the fix here to handle stderrStream more reliably
                    stderrStream?.apply {
                        write(errorMsg.toByteArray())
                        flush()
                    } ?: run {
                        System.err.print(errorMsg)
                    }
                }
            }
        } finally {
            stdoutStream?.close()
            stderrStream?.close()
        }
    }


    private fun changeDirectory(dir: String?) {
        if (dir == null) {
            currentDirectory = Paths.get(env["HOME"] ?: "/")
            return
        }
        val newPath = when {
            dir == "~" -> Paths.get(env["HOME"] ?: "/")
            Paths.get(dir).isAbsolute -> Paths.get(dir)
            else -> currentDirectory.resolve(dir).normalize()
        }
        if (Files.exists(newPath) && Files.isDirectory(newPath)) {
            currentDirectory = newPath
        } else {
            System.err.println("cd: $dir: No such file or directory")
        }
    }

    private fun handleTypeCommand(argument: String?, pathCommands: Map<String, String>): String {
        if (argument == null) return "type: missing argument"

        // Check if the command is a built-in command
        if (BuiltInCommands.entries.any { it.name.equals(argument, ignoreCase = true) }) {
            return "$argument is a shell builtin"
        }

        // Check if the command is in the predefined pathCommands map
        pathCommands[argument]?.let { return "$argument is $it" }

        // If the command isn't found in predefined pathCommands, search for it in the system path
        val pathDirs = System.getenv("PATH").split(File.pathSeparator)
        for (dir in pathDirs) {
            val filePath = File(dir, argument)
            if (filePath.exists() && filePath.canExecute()) {
                return "$argument is ${filePath.absolutePath}"
            }
        }

        return "$argument: not found"
    }

    private fun handleKillCommand(pid: String?, stderrStream: OutputStream?) {
        if (pid == null) {
            val errorMsg = "kill: missing argument\n"
            stderrStream?.write(errorMsg.toByteArray()) ?: System.err.print(errorMsg)
            return
        }
        try {
            pid.toInt()
            val process = ProcessBuilder("kill", "-9", pid).start()
            process.waitFor()
            if (process.exitValue() != 0) {
                val errorMsg = "kill: $pid: No such process\n"
                stderrStream?.write(errorMsg.toByteArray()) ?: System.err.print(errorMsg)
            }
        } catch (e: NumberFormatException) {
            val errorMsg = "kill: $pid: Invalid PID\n"
            stderrStream?.write(errorMsg.toByteArray()) ?: System.err.print(errorMsg)
        }
    }

    fun createFileWithDirs(path: String, append: Boolean): FileOutputStream? {
        return try {
            val file = File(path)
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs() // Ensure parent directories exist
            }
            FileOutputStream(file, append) // Open file for writing (appending if necessary)
        } catch (e: IOException) {
            System.err.println("Failed to create file: $path. Error: ${e.message}")
            null
        }
    }
}
