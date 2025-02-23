package commands

sealed class Command {
    data object Exit : Command()
    data class Cd(val directory: String?) : Command()
    data object Pwd : Command()
    data class Type(val argument: String?) : Command()
    data class Echo(val text: String) : Command()
    data class Kill(val pid: String?) : Command()
    data class ExternalCommand(val args: List<String>) : Command()
    data class Unknown(val input: String) : Command()
}
