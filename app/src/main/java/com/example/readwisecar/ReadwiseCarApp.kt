package com.example.readwisecar

import android.app.Application
import com.example.readwisecar.data.ArticleDatabase
import com.example.readwisecar.data.ReadwiseApi
import com.example.readwisecar.data.ReadwiseRepository
import com.example.readwisecar.data.TokenStore

class ReadwiseCarApp : Application() {
    lateinit var tokenStore: TokenStore
    lateinit var database: ArticleDatabase
    lateinit var repository: ReadwiseRepository

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        database = ArticleDatabase.create(this)
        repository = ReadwiseRepository(
            api = ReadwiseApi.create(),
            dao = database.articleDao(),
            tokenStore = tokenStore
        )
    }
}
