# Kotlin Shell Implementation

[![progress-banner](https://backend.codecrafters.io/progress/shell/4f30f11f-8d72-4f27-a193-d27fdc499579)](https://app.codecrafters.io/users/codecrafters-bot?r=2qF)

This is a Kotlin implementation of a POSIX-compliant shell, capable of interpreting shell commands, running external programs, and executing built-in commands like `cd`, `pwd`, `echo`, and more. The project is built using **Kotlin** and **Gradle**.

---

## Features Implemented

The following features have been implemented and tested:

### **Autocompletion**
- **Executable Completion**: Autocompletes executable names (e.g., `custom` → `custom_exe_2924`).
- **Missing Completions**: Handles cases where no autocompletion is available.
- **Completion with Arguments**: Autocompletes commands with arguments (e.g., `ech` → `echo`).
- **Built-in Completion**: Autocompletes built-in commands like `echo` and `exit`.

### **Redirection**
- **Append stderr**: Appends error output to a file (e.g., `ls nonexistent 2>> file.md`).
- **Append stdout**: Appends standard output to a file (e.g., `ls >> file.md`).
- **Redirect stderr**: Redirects error output to a file (e.g., `ls nonexistent 2> file.md`).
- **Redirect stdout**: Redirects standard output to a file (e.g., `ls > file.md`).

### **Quoting**
- **Executing a Quoted Executable**: Handles executables with spaces, quotes, and special characters.
- **Backslash within Double Quotes**: Processes backslashes inside double-quoted strings.
- **Backslash within Single Quotes**: Processes backslashes inside single-quoted strings.
- **Backslash Outside Quotes**: Handles backslashes outside quotes.
- **Double Quotes**: Processes double-quoted strings.
- **Single Quotes**: Processes single-quoted strings.

### **Navigation**
- **The `cd` Built-in**:
    - Home directory (`cd ~`).
    - Relative paths (`cd ./dir`).
    - Absolute paths (`cd /tmp/dir`).
- **The `pwd` Built-in**: Prints the current working directory.

### **Built-in Commands**
- **The `type` Built-in**:
    - Identifies executable files (e.g., `type cat`).
    - Identifies built-in commands (e.g., `type echo`).
- **The `echo` Built-in**: Prints arguments to standard output.
- **The `exit` Built-in**: Exits the shell with a status code.

### **REPL (Read-Eval-Print Loop)**
- Handles invalid commands gracefully (e.g., `invalid_command` → `command not found`).
- Prints a prompt (`$ `) for user input.

---

## How to Build and Run

### Prerequisites
- **Kotlin (>= 2.0)**
- **Gradle**

### Build the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/your-repo.git
   cd your-repo
   ```
2. Build the project using Gradle:
   ```bash
   ./gradlew build
   ```

### Run the Shell
1. Run the shell script:
   ```bash
   ./your_program.sh
   ```
2. The shell will start in interactive mode, displaying a prompt (`$ `). You can now enter commands.

---

## Ktlint Support

This project uses [Ktlint](https://ktlint.github.io/) to enforce Kotlin coding standards. Ktlint is integrated into the Gradle build process.

### Format Code
To automatically format your Kotlin code according to Ktlint rules, run:
```bash
./gradlew ktlintFormat
```

### Check Code Style
To check your Kotlin code for style violations without fixing them, run:
```bash
./gradlew ktlintCheck
```

### Ktlint Configuration
Ktlint is configured in the `build.gradle.kts` file:
```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}
```

---

## Usage Examples

### Autocompletion
```bash
$ custom<TAB>  # Autocompletes to custom_exe_2924
$ echo hello<TAB>  # Autocompletes to echo hello
```

### Redirection
```bash
$ ls > output.txt  # Redirects output to output.txt
$ ls nonexistent 2> error.txt  # Redirects errors to error.txt
```

### Quoting
```bash
$ echo "Hello, World!"  # Double quotes
$ echo 'Hello, World!'  # Single quotes
$ echo Hello\ World  # Backslash outside quotes
```

### Navigation
```bash
$ cd /tmp  # Change to /tmp directory
$ pwd  # Print current directory
```

### Built-in Commands
```bash
$ type echo  # Identifies echo as a built-in command
$ exit 0  # Exits the shell with status code 0
```

---

## Testing

The project includes a comprehensive test suite. To run the tests:
```bash
./gradlew test
```

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- Inspired by the ["Build Your Own Shell" Challenge](https://app.codecrafters.io/courses/shell/overview) on CodeCrafters.
- Built with ❤️ using Kotlin and Gradle.
