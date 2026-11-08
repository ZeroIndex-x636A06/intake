package com.zeroindex.intake.data.backup.remote

/**
 * One backup folder on a server. Every implementation writes to a temporary
 * name and renames into place, so a run cut off half-way never leaves a
 * truncated file that looks like a good backup.
 */
interface RemoteStore {
    /** Connects, makes sure the folder exists and is writable, and says what it reached. */
    suspend fun test(): String

    suspend fun put(name: String, bytes: ByteArray)

    /** File names directly in the backup folder. */
    suspend fun list(): List<String>

    suspend fun remove(name: String)
}

/**
 * A failure worded for the backup screen. [message] never contains a
 * password, key or passphrase - transports build it from the host and the
 * server's status, not from the request.
 */
class RemoteStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * SFTP's first connection to a host it has no pinned key for. The UI shows
 * [fingerprint] and pins it only if the user confirms; a headless run never
 * gets past this.
 */
class UnpinnedHostKeyException(val fingerprint: String, val keyType: String) :
    Exception("The server's identity is not confirmed yet.")

/** The pinned key no longer matches: either the server was reinstalled or someone is in the middle. */
class ChangedHostKeyException(val fingerprint: String) :
    Exception("The server's identity has changed since you confirmed it. Nothing was sent.")
