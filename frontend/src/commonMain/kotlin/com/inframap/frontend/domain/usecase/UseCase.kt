package com.inframap.frontend.domain.usecase

fun interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): R
}

fun interface NoParamUseCase<out R> {
    suspend operator fun invoke(): R
}
