package com.example.readwisecar.data

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface ReadwiseApi {
    @GET("api/v3/list/")
    suspend fun listDocuments(
        @Header("Authorization") auth: String,
        @Query("pageCursor") pageCursor: String? = null,
        @Query("id") id: String? = null,
        @Query("withHtmlContent") withHtmlContent: Boolean = false
    ): Response<ReadwiseListResponse>

    @PATCH("api/v3/update/{id}/")
    suspend fun updateDocument(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    companion object {
        fun create(): ReadwiseApi = Retrofit.Builder()
            .baseUrl("https://readwise.io/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReadwiseApi::class.java)
    }
}

data class ReadwiseListResponse(
    val nextPageCursor: String?,
    val results: List<ReaderDocumentDto>
)

data class ReaderDocumentDto(
    val id: String,
    val title: String?,
    val author: String?,
    val category: String?,
    val location: String?,
    val parent_id: String?,
    val word_count: Int?,
    val reading_progress: Float?,
    val image_url: String?,
    val html_content: String?,
    val updated_at: String?
)
