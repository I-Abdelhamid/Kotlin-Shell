package com.shell.console

import com.shell.util.PathCommandsLoader

class ConsoleReader(private val pathCommandsLoader: PathCommandsLoader) {

    private val buffer = StringBuilder() // Stores the input from the user
    private var cursorPosition = 0 // Tracks the cursor position within the buffer
    private var lastTabPressTime: Long? = null
    private var lastTabInput: String? = null // Tracks input at the last tab press
    private val builtins = listOf("echo", "exit", "type", "cd", "pwd", "kill") // Built-in commands

    fun readLine(): String {
        buffer.clear() // Reset buffer for new input
        cursorPosition = 0 // Reset cursor position
        while (true) {
            val input = System.`in`.read()
            when (input) {
                -1 -> return buffer.toString() // End of input
                9 -> handleTabCompletion() // Tab key for auto-completion
                10, 13 -> { // Enter key
                    val result = buffer.toString().trimEnd()
                    print("\r\n\u001B[K") // Clear line after Enter
                    System.out.flush()
                    buffer.clear()
                    cursorPosition = 0
                    return result
                }
                127 -> { // Backspace
                    if (buffer.isNotEmpty() && cursorPosition > 0) {
                        buffer.deleteCharAt(cursorPosition - 1)
                        cursorPosition--
                        print("\b \b") // Delete character from console
                        System.out.flush()
                    }
                }
                else -> {
                    val char = input.toChar()
                    if (char.isISOControl() && char != '\t') continue // Skip control chars except tab
                    buffer.insert(cursorPosition, char)
                    cursorPosition++
                    print(char)
                    System.out.flush()
                }
            }
        }
    }

    private fun handleTabCompletion() {
        val currentInput = buffer.toString()
        val trimmedInput = currentInput.trimEnd()

        if (!isValidForCompletion(currentInput, trimmedInput)) return

        val words = trimmedInput.split("\\s+".toRegex())
        val (currentWord, prefix) = extractWordInfo(words, currentInput)
        val completions = getCompletions(currentWord)

        handleCompletions(currentWord, completions, prefix, currentInput)
    }

    internal fun isValidForCompletion(currentInput: String, trimmedInput: String): Boolean {
        val cursorAtEnd = cursorPosition == currentInput.length
        return cursorAtEnd && trimmedInput.isNotEmpty()
    }

    internal fun extractWordInfo(words: List<String>, currentInput: String): Pair<String, String> {
        val currentWord = if (currentInput.endsWith(" ")) "" else words.last()
        val prefix = if (words.size > 1) words.dropLast(1).joinToString(" ") + " " else ""
        return currentWord to prefix
    }

    internal fun getCompletions(currentWord: String): List<String> {
        val pathCommands = pathCommandsLoader.load(System.getenv("PATH") ?: "")
        val allCommands = (builtins + pathCommands.keys).distinct()
        return allCommands.filter { it.startsWith(currentWord) }.sorted()
    }

    internal fun handleCompletions(currentWord: String, completions: List<String>, prefix: String, currentInput: String) {
        when {
            completions.isEmpty() && currentWord.isNotEmpty() && !currentInput.endsWith(" ") -> {
                beep()
            }
            completions.size == 1 -> {
                completeSingle(completions[0], currentWord, currentInput)
            }
            completions.size > 1 -> {
                handleMultipleCompletions(completions, currentWord, currentInput)
            }
        }
    }

    private fun beep() {
        print("\u0007")
        System.out.flush()
    }

    internal fun completeSingle(completion: String, currentWord: String, currentInput: String) {
        clearCurrentWord(currentWord)
        buffer.delete(buffer.length - currentWord.length, buffer.length)
        buffer.append(completion)
        print(completion)

        if (!currentInput.endsWith(" ")) {
            buffer.append(" ")
            print(" ")
        }
        cursorPosition = buffer.length
        System.out.flush()
        updateTabTracking()
    }

    internal fun handleMultipleCompletions(completions: List<String>, currentWord: String, currentInput: String) {
        val lcp = longestCommonPrefix(completions)
        val isDoubleTab = isDoubleTabPress(currentInput)

        if (isDoubleTab) {
            showAllCompletions(completions)
        } else {
            completeToPrefix(lcp, currentWord)
        }
        updateTabTracking()
    }

    private fun isDoubleTabPress(currentInput: String): Boolean {
        return lastTabPressTime != null &&
            System.currentTimeMillis() - lastTabPressTime!! < 1000 &&
            currentInput == lastTabInput
    }

    private fun showAllCompletions(completions: List<String>) {
        print("\r\n${completions.joinToString("  ")}\r\n")
        printPrompt()
        print(buffer.toString())
        System.out.flush()
    }

    private fun completeToPrefix(lcp: String, currentWord: String) {
        if (lcp != currentWord) {
            clearCurrentWord(currentWord)
            buffer.delete(buffer.length - currentWord.length, buffer.length)
            buffer.append(lcp)
            print(lcp)
            cursorPosition = buffer.length
            System.out.flush()
        } else {
            beep()
        }
    }

    private fun clearCurrentWord(currentWord: String) {
        repeat(currentWord.length) {
            print("\b \b")
        }
    }

    private fun updateTabTracking() {
        lastTabPressTime = System.currentTimeMillis()
        lastTabInput = buffer.toString()
    }

    internal fun longestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        if (strings.size == 1) return strings[0]
        var prefix = strings[0]
        for (i in 1 until strings.size) {
            prefix = prefix.commonPrefixWith(strings[i])
            if (prefix.isEmpty()) break
        }
        return prefix
    }

    private fun printPrompt() {
        print("\r\u001B[K$ ")
        System.out.flush()
    }
}
