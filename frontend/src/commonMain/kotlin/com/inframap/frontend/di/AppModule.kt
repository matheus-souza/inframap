package com.inframap.frontend.di

fun appModules(baseUrl: String) =
    listOf(
        dataModule(baseUrl),
        domainModule,
        presentationModule,
    )
