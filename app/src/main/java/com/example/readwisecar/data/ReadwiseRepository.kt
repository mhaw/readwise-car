package com.example.readwisecar.data

import kotlinx.coroutines.delay

class ReadwiseRepository(
    private val api: ReadwiseApi,
    private val dao: ArticleDao,
    private val tokenStore: TokenStore
) {
    private val allowedCategories = setOf("article", "email", "rss")
    private val allowedLocations = setOf("new", "later", "shortlist", "feed")

    private fun authHeader(): String? {
        val token = tokenStore.apiToken
        return if (token.isBlank()) null else "Token $token"
    }

    fun observeCounts() = dao.observeTotalCount()
    fun observeCachedHtmlCount() = dao.observeCachedHtmlCount()

    suspend fun getArticlesForLocation(location: String): List<ArticleEntity> =
        dao.getByLocation(location)

    suspend fun getNextArticleInLocation(location: String, currentId: String): ArticleEntity? {
        val list = dao.getByLocation(location)
        val idx = list.indexOfFirst { it.id == currentId }
        return if (idx in 0 until list.lastIndex) list[idx + 1] else null
    }

    suspend fun syncFromReadwise(onStatus: (String) -> Unit = {}): Result<Int> = runCatching {
        val auth = authHeader() ?: error("Please save your Readwise token first.")
        onStatus("Fetching article list...")

        val fetched = mutableListOf<ArticleEntity>()
        var cursor: String? = null
        var pages = 0

        do {
            val resp = api.listDocuments(auth = auth, pageCursor = cursor)
            if (resp.code() == 429) {
                val waitSec = resp.headers()["Retry-After"]?.toLongOrNull() ?: 10L
                onStatus("Rate limited, waiting ${waitSec}s...")
                delay(waitSec * 1000)
                continue
            }
            if (!resp.isSuccessful) error("API error ${resp.code()}")

            val body = resp.body() ?: break
            for (dto in body.results) {
                if (dto.parent_id != null) continue
                if (dto.category !in allowedCategories) continue
                if (dto.location !in allowedLocations) continue

                val existing = dao.getById(dto.id)
                fetched += ArticleEntity(
                    id = dto.id,
                    title = dto.title?.ifBlank { "Untitled" } ?: "Untitled",
                    author = dto.author?.ifBlank { "Readwise" } ?: "Readwise",
                    category = dto.category ?: "article",
                    location = dto.location ?: "later",
                    wordCount = dto.word_count ?: 0,
                    readingProgress = dto.reading_progress ?: existing?.readingProgress ?: 0f,
                    imageUrl = dto.image_url,
                    htmlContent = existing?.htmlContent,
                    updatedAt = dto.updated_at ?: "",
                    lastPlayedChunk = existing?.lastPlayedChunk ?: 0,
                    totalChunks = existing?.totalChunks ?: 0
                )
            }
            cursor = body.nextPageCursor
            pages++
        } while (cursor != null && pages < 3)

        dao.upsertAll(fetched)
        dao.deleteArchived()

        // Pre-cache full HTML for offline driving while staying under Readwise's 20 req/min limit
        val toCache = dao.getMissingHtml(limit = 12)
        toCache.forEachIndexed { i, item ->
            onStatus("Caching offline text (${i + 1}/${toCache.size})...")
            val r = api.listDocuments(auth = auth, id = item.id, withHtmlContent = true)
            if (r.code() == 429) {
                onStatus("Cached $i articles (rate limit reached; remaining load on demand).")
                return@runCatching fetched.size
            }
            val html = r.body()?.results?.firstOrNull()?.html_content
            if (!html.isNullOrBlank()) {
                dao.updateHtml(item.id, html)
            }
            delay(3200)
        }

        onStatus("Sync complete: ${fetched.size} articles ready.")
        fetched.size
    }

    suspend fun getArticleWithHtml(docId: String): ArticleEntity? {
        val local = dao.getById(docId) ?: return null
        if (!local.htmlContent.isNullOrBlank()) return local

        val auth = authHeader() ?: return local
        val resp = api.listDocuments(auth = auth, id = docId, withHtmlContent = true)
        val html = resp.body()?.results?.firstOrNull()?.html_content
        return if (!html.isNullOrBlank()) {
            dao.updateHtml(docId, html)
            local.copy(htmlContent = html)
        } else local
    }

    suspend fun saveProgress(docId: String, chunkIndex: Int, totalChunks: Int) {
        if (totalChunks <= 0) return
        val fraction = ((chunkIndex + 1).toFloat() / totalChunks).coerceIn(0f, 1f)
        dao.updateLocalProgress(docId, chunkIndex, totalChunks, fraction)
        val auth = authHeader() ?: return
        runCatching {
            api.updateDocument(auth, docId, mapOf("reading_progress" to fraction))
        }
    }

    suspend fun archiveArticle(docId: String) {
        dao.updateLocation(docId, "archive")
        val auth = authHeader() ?: return
        runCatching {
            api.updateDocument(
                auth,
                docId,
                mapOf("location" to "archive", "reading_progress" to 1.0f)
            )
        }
    }
}
