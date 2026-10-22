package com.mircode.app.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mircode.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class MirCodeApiClient(
    private var profile: ConnectionProfile
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun updateProfile(newProfile: ConnectionProfile) {
        this.profile = newProfile
    }

    private fun newRequestBuilder(path: String): Request.Builder {
        val url = "${profile.baseUrl}$path"
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${profile.token}")
            .addHeader("Accept", "application/json")
    }

    private suspend fun executeRequest(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            // Automatically retry once if previous connection was dropped / closed
            client.newCall(request).execute()
        }
    }

    suspend fun verifyAuth(): Result<Boolean> = runCatching {
        val request = newRequestBuilder("/api/v1/auth/verify").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Auth failed: HTTP ${it.code}")
            true
        }
    }

    suspend fun getSystemInfo(): Result<SystemInfo> = runCatching {
        val request = newRequestBuilder("/api/v1/system/info").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to get system info: ${it.code}")
            gson.fromJson(it.body?.string(), SystemInfo::class.java)
        }
    }

    suspend fun browseFs(path: String?): Result<BrowseResult> = runCatching {
        val pathParam = if (!path.isNullOrEmpty()) "?path=" + java.net.URLEncoder.encode(path, "UTF-8") else ""
        val request = newRequestBuilder("/api/v1/fs/browse$pathParam").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to browse: ${it.code}")
            gson.fromJson(it.body?.string(), BrowseResult::class.java)
        }
    }

    suspend fun getWorkspaces(): Result<List<WorkspaceEntry>> = runCatching {
        val request = newRequestBuilder("/api/v1/workspaces").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to get workspaces: ${it.code}")
            val type = object : TypeToken<List<WorkspaceEntry>>() {}.type
            gson.fromJson<List<WorkspaceEntry>>(it.body?.string(), type) ?: emptyList()
        }
    }

    suspend fun addWorkspace(path: String): Result<WorkspaceEntry> = runCatching {
        val json = gson.toJson(mapOf("path" to path))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/workspaces").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to add workspace: ${it.code}")
            gson.fromJson(it.body?.string(), WorkspaceEntry::class.java)
        }
    }

    suspend fun deleteWorkspace(path: String): Result<Boolean> = runCatching {
        val encoded = java.net.URLEncoder.encode(path, "UTF-8")
        val request = newRequestBuilder("/api/v1/workspaces?path=$encoded").delete().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to delete workspace: ${it.code}")
            true
        }
    }

    suspend fun activateWorkspace(path: String): Result<Boolean> = runCatching {
        val json = gson.toJson(mapOf("path" to path))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/workspaces/activate").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to activate workspace: ${it.code}")
            true
        }
    }

    suspend fun getGitStatus(): Result<StatusResult> = runCatching {
        val request = newRequestBuilder("/api/v1/git/status").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to get git status: ${it.code}")
            gson.fromJson(it.body?.string(), StatusResult::class.java)
        }
    }

    suspend fun getDiff(file: String, staged: Boolean): Result<DiffResult> = runCatching {
        val encodedFile = java.net.URLEncoder.encode(file, "UTF-8")
        val request = newRequestBuilder("/api/v1/git/diff?file=$encodedFile&staged=$staged").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Failed to get diff: ${it.code}")
            gson.fromJson(it.body?.string(), DiffResult::class.java)
        }
    }

    suspend fun stageFiles(paths: List<String>): Result<Boolean> = runCatching {
        val json = gson.toJson(mapOf("paths" to paths))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/stage").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Stage failed: ${it.code}")
            true
        }
    }

    suspend fun unstageFiles(paths: List<String>): Result<Boolean> = runCatching {
        val json = gson.toJson(mapOf("paths" to paths))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/unstage").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Unstage failed: ${it.code}")
            true
        }
    }

    suspend fun discardFiles(paths: List<String>): Result<Boolean> = runCatching {
        val json = gson.toJson(mapOf("paths" to paths))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/discard").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Discard failed: ${it.code}")
            true
        }
    }

    suspend fun commit(message: String, amend: Boolean): Result<String> = runCatching {
        val json = gson.toJson(mapOf("message" to message, "amend" to amend))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/commit").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Commit failed: ${it.code}")
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val result: Map<String, Any> = gson.fromJson(it.body?.string(), mapType)
            result["output"]?.toString() ?: "Commit successful"
        }
    }

    suspend fun listBranches(): Result<List<BranchInfo>> = runCatching {
        val request = newRequestBuilder("/api/v1/git/branches").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("List branches failed: ${it.code}")
            val listType = object : TypeToken<List<BranchInfo>>() {}.type
            gson.fromJson(it.body?.string(), listType)
        }
    }

    suspend fun branchAction(action: String, branch: String): Result<String> = runCatching {
        val json = gson.toJson(mapOf("action" to action, "branch" to branch))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/branch").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Branch action $action failed: ${it.code}")
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val result: Map<String, Any> = gson.fromJson(it.body?.string(), mapType)
            result["output"]?.toString() ?: "Branch $action success"
        }
    }

    suspend fun sync(action: String): Result<String> = runCatching {
        val json = gson.toJson(mapOf("action" to action))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/sync").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Sync $action failed: ${it.code}")
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val result: Map<String, Any> = gson.fromJson(it.body?.string(), mapType)
            result["output"]?.toString() ?: "Sync $action complete"
        }
    }

    suspend fun listStashes(): Result<List<StashItem>> = runCatching {
        val request = newRequestBuilder("/api/v1/git/stashes").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("List stashes failed: ${it.code}")
            val listType = object : TypeToken<List<StashItem>>() {}.type
            gson.fromJson(it.body?.string(), listType)
        }
    }

    suspend fun stashAction(action: String, message: String? = null, index: Int? = null): Result<String> = runCatching {
        val map = mutableMapOf<String, Any>("action" to action)
        if (message != null) map["message"] = message
        if (index != null) map["index"] = index
        val json = gson.toJson(map)
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/stash").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Stash $action failed: ${it.code}")
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val result: Map<String, Any> = gson.fromJson(it.body?.string(), mapType)
            result["output"]?.toString() ?: "Stash $action complete"
        }
    }

    suspend fun resolveConflict(path: String, strategy: String, content: String? = null): Result<Boolean> = runCatching {
        val map = mutableMapOf<String, Any>("path" to path, "strategy" to strategy)
        if (content != null) map["content"] = content
        val json = gson.toJson(map)
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/git/resolve").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Resolve conflict failed: ${it.code}")
            true
        }
    }

    suspend fun getFileContent(path: String): Result<String> = runCatching {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        val request = newRequestBuilder("/api/v1/file/content?path=$encodedPath").get().build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Read file failed: ${it.code}")
            val res = gson.fromJson(it.body?.string(), FileContentResult::class.java)
            res.content
        }
    }

    suspend fun saveFileContent(path: String, content: String): Result<Boolean> = runCatching {
        val json = gson.toJson(mapOf("path" to path, "content" to content))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/file/content").put(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Save file failed: ${it.code}")
            true
        }
    }

    suspend fun toggleReview(path: String, reviewed: Boolean, reviewer: String = "MobileUser"): Result<ReviewItem> = runCatching {
        val json = gson.toJson(mapOf("path" to path, "reviewed" to reviewed, "reviewer" to reviewer))
        val body = json.toRequestBody(jsonMediaType)
        val request = newRequestBuilder("/api/v1/review/toggle").post(body).build()
        val response = executeRequest(request)
        response.use {
            if (!it.isSuccessful) throw IOException("Toggle review failed: ${it.code}")
            gson.fromJson(it.body?.string(), ReviewItem::class.java)
        }
    }
}
