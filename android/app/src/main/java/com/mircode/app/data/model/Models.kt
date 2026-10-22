package com.mircode.app.data.model

import com.google.gson.annotations.SerializedName

data class ConnectionProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val host: String = "127.0.0.1",
    val port: Int = 18980,
    val token: String,
    val connectMode: String = "relay", // "relay", "tailcat", "direct"
    val pairCode: String? = null,
    val relayUrl: String = "https://relay.mircode.dev",
    val tailcatAddress: String? = null,
    val lastConnected: Long = System.currentTimeMillis()
) {
    val baseUrl: String
        get() = when (connectMode) {
            "relay" -> {
                val cleanRelay = relayUrl.trimEnd('/')
                val cleanCode = pairCode?.replace(" ", "")?.replace("-", "") ?: ""
                "$cleanRelay/relay/$cleanCode"
            }
            else -> "http://$host:$port"
        }

    val wsUrl: String
        get() = when (connectMode) {
            "relay" -> {
                val cleanRelay = relayUrl.trimEnd('/')
                val wsBase = cleanRelay.replace("https://", "wss://").replace("http://", "ws://")
                val cleanCode = pairCode?.replace(" ", "")?.replace("-", "") ?: ""
                "$wsBase/relay/$cleanCode/api/v1/ws?token=$token"
            }
            else -> "ws://$host:$port/api/v1/ws?token=$token"
        }
}

data class SystemInfo(
    @SerializedName("hostname") val hostname: String,
    @SerializedName("os") val os: String,
    @SerializedName("arch") val arch: String,
    @SerializedName("gitVersion") val gitVersion: String,
    @SerializedName("activeWorkspace") val activeWorkspace: String
)

data class BrowseEntry(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("isDir") val isDir: Boolean,
    @SerializedName("isGitRepo") val isGitRepo: Boolean,
    @SerializedName("size") val size: Long,
    @SerializedName("modTime") val modTime: String
)

data class BrowseResult(
    @SerializedName("currentPath") val currentPath: String,
    @SerializedName("parentPath") val parentPath: String,
    @SerializedName("homePath") val homePath: String,
    @SerializedName("entries") val entries: List<BrowseEntry> = emptyList()
)

data class FileStatus(
    @SerializedName("path") val path: String,
    @SerializedName("oldPath") val oldPath: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("staged") val staged: Boolean,
    @SerializedName("additions") val additions: Int = 0,
    @SerializedName("deletions") val deletions: Int = 0,
    @SerializedName("reviewed") val reviewed: Boolean = false,
    @SerializedName("modifiedSinceReview") val modifiedSinceReview: Boolean = false,
    @SerializedName("reviewedAt") val reviewedAt: Long? = null
)

data class StatusResult(
    @SerializedName("workspace") val workspace: String = "",
    @SerializedName("branch") val branch: String = "",
    @SerializedName("upstream") val upstream: String? = null,
    @SerializedName("ahead") val ahead: Int = 0,
    @SerializedName("behind") val behind: Int = 0,
    @SerializedName("clean") val clean: Boolean = true,
    @SerializedName("stagedFiles") val stagedFiles: List<FileStatus> = emptyList(),
    @SerializedName("unstagedFiles") val unstagedFiles: List<FileStatus> = emptyList(),
    @SerializedName("untrackedFiles") val untrackedFiles: List<FileStatus> = emptyList(),
    @SerializedName("conflictedFiles") val conflictedFiles: List<FileStatus> = emptyList(),
    @SerializedName("totalReviewed") val totalReviewed: Int = 0,
    @SerializedName("totalChanged") val totalChanged: Int = 0
)

data class BranchInfo(
    @SerializedName("name") val name: String,
    @SerializedName("isCurrent") val isCurrent: Boolean,
    @SerializedName("isRemote") val isRemote: Boolean,
    @SerializedName("commitSha") val commitSha: String,
    @SerializedName("message") val message: String
)

data class StashItem(
    @SerializedName("index") val index: Int,
    @SerializedName("name") val name: String,
    @SerializedName("branch") val branch: String,
    @SerializedName("message") val message: String
)

data class ReviewItem(
    @SerializedName("filePath") val filePath: String,
    @SerializedName("blobSha") val blobSha: String,
    @SerializedName("contentSha256") val contentSha256: String,
    @SerializedName("reviewed") val reviewed: Boolean,
    @SerializedName("modifiedSinceReview") val modifiedSinceReview: Boolean,
    @SerializedName("reviewedAt") val reviewedAt: Long,
    @SerializedName("reviewedBy") val reviewedBy: String
)

data class DiffResult(
    @SerializedName("file") val file: String,
    @SerializedName("staged") val staged: Boolean,
    @SerializedName("diff") val diff: String
)

data class FileContentResult(
    @SerializedName("path") val path: String,
    @SerializedName("content") val content: String
)

data class WSMessage(
    @SerializedName("type") val type: String,
    @SerializedName("timestamp") val timestamp: Long
)
