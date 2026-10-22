package com.mircode.app.data.repository

import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.model.FileStatus
import com.mircode.app.data.model.StatusResult
import com.mircode.app.data.model.WorkspaceEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorkspaceRepository {
    private val _status = MutableStateFlow<StatusResult?>(null)
    val status: StateFlow<StatusResult?> = _status.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceEntry>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceEntry>> = _workspaces.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun refreshStatus(client: MirCodeApiClient) {
        _isRefreshing.value = true
        client.getGitStatus()
            .onSuccess {
                _status.value = it
            }
        _isRefreshing.value = false
    }

    suspend fun refreshWorkspaces(client: MirCodeApiClient) {
        client.getWorkspaces()
            .onSuccess {
                _workspaces.value = it
            }
    }

    fun updateStatus(newStatus: StatusResult) {
        _status.value = newStatus
    }

    fun optimisticToggleReview(filePath: String, isReviewed: Boolean) {
        val current = _status.value ?: return
        fun updateList(list: List<FileStatus>): List<FileStatus> {
            return list.map {
                if (it.path == filePath) {
                    it.copy(reviewed = isReviewed, modifiedSinceReview = false)
                } else it
            }
        }

        val updatedStaged = updateList(current.stagedFiles)
        val updatedUnstaged = updateList(current.unstagedFiles)
        val updatedUntracked = updateList(current.untrackedFiles)

        val reviewedSet = mutableSetOf<String>()
        (updatedStaged + updatedUnstaged + updatedUntracked).forEach {
            if (it.reviewed) reviewedSet.add(it.path)
        }

        _status.value = current.copy(
            stagedFiles = updatedStaged,
            unstagedFiles = updatedUnstaged,
            untrackedFiles = updatedUntracked,
            totalReviewed = reviewedSet.size
        )
    }

    fun optimisticStage(path: String) {
        val current = _status.value ?: return
        val target = current.unstagedFiles.find { it.path == path }
            ?: current.untrackedFiles.find { it.path == path } ?: return

        val newUnstaged = current.unstagedFiles.filterNot { it.path == path }
        val newUntracked = current.untrackedFiles.filterNot { it.path == path }
        val newStaged = current.stagedFiles + target.copy(staged = true)

        _status.value = current.copy(
            stagedFiles = newStaged,
            unstagedFiles = newUnstaged,
            untrackedFiles = newUntracked
        )
    }

    fun optimisticUnstage(path: String) {
        val current = _status.value ?: return
        val target = current.stagedFiles.find { it.path == path } ?: return

        val newStaged = current.stagedFiles.filterNot { it.path == path }
        val newUnstaged = current.unstagedFiles + target.copy(staged = false)

        _status.value = current.copy(
            stagedFiles = newStaged,
            unstagedFiles = newUnstaged
        )
    }

    fun clear() {
        _status.value = null
        _workspaces.value = emptyList()
    }
}
