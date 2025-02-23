package com.shell.parser

import com.shell.util.Redirections
import commands.Command

class InputParser {
    private val SINGLE_QUOTE = '\''
    private val DOUBLE_QUOTE = '"'
    private val BACKSLASH = '\\'

    fun parse(input: String, pathCommands: Map<String, String>): Pair<Command, Redirections> {
        val (commandPart, redirections) = parseRedirections(input)
        val args = parseArguments(commandPart)
        if (args.isEmpty()) return Pair(Command.Unknown(commandPart), redirections)
        val command = when (args[0]) {
            "exit" -> Command.Exit
            "cd" -> Command.Cd(args.getOrNull(1))
            "pwd" -> Command.Pwd
            "type" -> Command.Type(args.getOrNull(1))
            "echo" -> Command.Echo(args.drop(1).joinToString(" "))
            "kill" -> Command.Kill(args.getOrNull(1))
            else -> if (pathCommands.containsKey(args[0])) {
                Command.ExternalCommand(args)
            } else {
                Command.Unknown(args[0])
            }
        }
        return Pair(command, redirections)
    }

    internal fun parseRedirections(input: String): Pair<String, Redirections> {
        var commandPart = input
        var stdout: String? = null
        var stderr: String? = null
        var stdoutAppend = false
        var stderrAppend = false
        val pattern = "\\s*((?:1)?>>|(?:1)?>|2>>|2>)\\s*([^\\s>]+)".toRegex()
        val matches = pattern.findAll(input)
        for (match in matches) {
            val (operator, file) = match.destructured
            when (operator.trim()) {
                "2>>" -> {
                    stderr = file.trim()
                    stderrAppend = true
                }

                "2>" -> {
                    stderr = file.trim()
                    stderrAppend = false
                }

                ">>", "1>>" -> {
                    stdout = file.trim()
                    stdoutAppend = true
                }

                ">", "1>" -> {
                    stdout = file.trim()
                    stdoutAppend = false
                }
            }
            commandPart = commandPart.replace(match.value, "")
        }
        return Pair(commandPart.trim(), Redirections(stdout, stderr, stdoutAppend, stderrAppend))
    }

    internal fun parseArguments(input: String): List<String> {
        val args = mutableListOf<String>()
        val currentArg = StringBuilder()
        var i = 0
        while (i < input.length) {
            when (val c = input[i]) {
                ' ', '\t', '\n', '\r' -> {
                    if (currentArg.isNotEmpty()) {
                        args.add(currentArg.toString())
                        currentArg.clear()
                    }
                    i++
                }

                SINGLE_QUOTE -> {
                    // Single-quoted: take everything literally (backslashes remain)
                    i++ // skip opening '
                    while (i < input.length && input[i] != SINGLE_QUOTE) {
                        currentArg.append(input[i])
                        i++
                    }
                    if (i < input.length && input[i] == SINGLE_QUOTE) {
                        i++ // skip closing '
                    }
                }

                DOUBLE_QUOTE -> {
                    // Double-quoted: backslash escapes ", \, $, and `
                    i++ // skip opening "
                    while (i < input.length && input[i] != DOUBLE_QUOTE) {
                        if (input[i] == BACKSLASH && i + 1 < input.length) {
                            val next = input[i + 1]
                            if (next == DOUBLE_QUOTE || next == BACKSLASH || next == '$' || next == '`') {
                                currentArg.append(next)
                                i += 2
                            } else {
                                currentArg.append(BACKSLASH)
                                i++
                            }
                        } else {
                            currentArg.append(input[i])
                            i++
                        }
                    }
                    if (i < input.length && input[i] == DOUBLE_QUOTE) {
                        i++ // skip closing "
                    }
                }

                BACKSLASH -> {
                    // Outside quotes: backslash escapes next character.
                    if (i + 1 < input.length) {
                        currentArg.append(input[i + 1])
                        i += 2
                    } else {
                        i++
                    }
                }

                else -> {
                    currentArg.append(c)
                    i++
                }
            }
        }
        if (currentArg.isNotEmpty()) {
            args.add(currentArg.toString())
        }
        return args
    }
}
