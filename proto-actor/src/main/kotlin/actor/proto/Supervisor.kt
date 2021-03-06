package actor.proto

interface Supervisor {
    val children: Collection<PID>
    fun escalateFailure(reason: Exception, who: PID)
    fun restartChildren(reason: Exception, vararg pids: PID)
    fun stopChildren(vararg pids: PID)
    fun resumeChildren(vararg pids: PID)
}