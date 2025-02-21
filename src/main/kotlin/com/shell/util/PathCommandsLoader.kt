package com.shell.util

import java.io.File
import java.nio.file.Paths

object PathCommandsLoader {
    fun load(path: String): Map<String, String> {
        val commands = mutableMapOf<String, String>()
        path.split(File.pathSeparator)
            .map { Paths.get(it).toAbsolutePath().normalize().toString() }
            .forEach { pathDir ->
                val directory = File(pathDir)
                if (directory.exists() && directory.isDirectory) {
                    directory.listFiles()?.filter { it.isFile && it.canExecute() }
                        ?.forEach { file -> commands.putIfAbsent(file.name, file.absolutePath) }
                }
            }
        return commands
    }
}
