package com.shell.util;

data class Redirections(
        val stdout: String? = null,
        val stderr: String? = null,
        val stdoutAppend: Boolean = false,
        val stderrAppend: Boolean = false
)
