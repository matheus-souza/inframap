package com.inframap.frontend.ui.splash

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.storage.InterruptedRouteStore
import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.data.storage.draft.FormDraftStore
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.usecase.auth.GetCurrentUserUseCase
import com.inframap.frontend.domain.usecase.auth.GetSetupStatusUseCase
import com.inframap.frontend.fakes.FakeAuthRepository
import com.inframap.frontend.fakes.FakeEpochClock
import com.inframap.frontend.fakes.FakeLocalStorage
import com.inframap.frontend.navigation.Route
import com.inframap.frontend.ui.session.SessionResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private fun makeVm(
        repo: FakeAuthRepository = FakeAuthRepository(),
        sessionResume: SessionResume? = null,
        scope: CoroutineScope? = null,
    ) = SplashViewModel(
        GetSetupStatusUseCase(repo),
        GetCurrentUserUseCase(repo),
        sessionResume = sessionResume,
        scope = scope,
    )

    @Test
    fun notOnboardedNavigatesToOnboarding() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getSetupStatusResult =
                        ApiResult.Success(
                            SetupStatus(onboardingCompleted = false, systemInstanceId = ""),
                            requestId = "",
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToOnboarding>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun onboardedAndAuthenticatedNavigatesToDashboard() =
        runTest {
            val vm = makeVm(scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToDashboard>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun onboardedButUnauthenticatedNavigatesToLogin() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getCurrentUserResult =
                        ApiResult.Error(
                            code = "UNAUTH",
                            message = "Unauthenticated",
                            requestId = "",
                            httpStatus = 401,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToLogin>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun setupStatusErrorNavigatesToLogin() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getSetupStatusResult =
                        ApiResult.Error(
                            code = "SERVER_ERROR",
                            message = "Server error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToLogin>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun initialStateIsLoading() {
        val vm = makeVm()
        assertTrue(vm.state.value.isLoading)
        vm.clear()
    }

    @Test
    fun authMeNetworkErrorNavigatesToLogin() =
        runTest {
            val repo =
                FakeAuthRepository(
                    getCurrentUserResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToLogin>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun authenticatedBootRegistersSessionOwnerViaSessionResume() =
        runTest {
            val storage = FakeLocalStorage()
            val clock = FakeEpochClock(1_000_000L)
            val ownerStore = SessionOwnerStore(storage)
            val draftStore = FormDraftStore(storage, clock, ownerStore)
            val sessionResume = SessionResume(ownerStore, draftStore)
            val user = User(id = "user-xyz", username = "admin")
            val repo = FakeAuthRepository(getCurrentUserResult = ApiResult.Success(user, requestId = ""))

            val vm = makeVm(repo = repo, sessionResume = sessionResume, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToDashboard>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("user-xyz", ownerStore.currentOwnerId())
            vm.clear()
        }

    @Test
    fun authenticatedBootEmitsResumePreviousRouteWhenSessionOwnerMatches() =
        runTest {
            val storage = FakeLocalStorage()
            val clock = FakeEpochClock(1_000_000L)
            val ownerStore = SessionOwnerStore(storage)
            ownerStore.setOwner("user-abc") // pre-set same owner
            val draftStore = FormDraftStore(storage, clock, ownerStore)
            val sessionResume = SessionResume(ownerStore, draftStore)
            val user = User(id = "user-abc", username = "admin")
            val repo = FakeAuthRepository(getCurrentUserResult = ApiResult.Success(user, requestId = ""))

            val vm = makeVm(repo = repo, sessionResume = sessionResume, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.ResumePreviousRoute>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun authenticatedBootWithDifferentOwnerNavigatesToDashboardAndPurgesStoredRoute() =
        runTest {
            val storage = FakeLocalStorage()
            val clock = FakeEpochClock(1_000_000L)
            val ownerStore = SessionOwnerStore(storage)
            ownerStore.setOwner("user-previous")
            val draftStore = FormDraftStore(storage, clock, ownerStore)
            val routeStore = InterruptedRouteStore(storage, clock, ownerStore)
            routeStore.save(Route.CreateSubnet())
            assertNotNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))

            val sessionResume = SessionResume(ownerStore, draftStore, routeStore)
            val user = User(id = "user-new", username = "newadmin")
            val repo = FakeAuthRepository(getCurrentUserResult = ApiResult.Success(user, requestId = ""))

            val vm = makeVm(repo = repo, sessionResume = sessionResume, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToDashboard>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("user-new", ownerStore.currentOwnerId())
            assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
            vm.clear()
        }

    @Test
    fun authenticatedBootWithBlankUserIdNavigatesToDashboardAndPurgesSession() =
        runTest {
            val storage = FakeLocalStorage()
            val clock = FakeEpochClock(1_000_000L)
            val ownerStore = SessionOwnerStore(storage)
            ownerStore.setOwner("user-old")
            val draftStore = FormDraftStore(storage, clock, ownerStore)
            val routeStore = InterruptedRouteStore(storage, clock, ownerStore)
            routeStore.save(Route.CreateSubnet())

            val sessionResume = SessionResume(ownerStore, draftStore, routeStore)
            val user = User(id = "  ", username = "anon")
            val repo = FakeAuthRepository(getCurrentUserResult = ApiResult.Success(user, requestId = ""))

            val vm = makeVm(repo = repo, sessionResume = sessionResume, scope = this)

            vm.effects.test {
                vm.checkAuthState()
                assertIs<SplashEffect.NavigateToDashboard>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(ownerStore.currentOwnerId())
            assertNull(storage.get(InterruptedRouteStore.KEY_INTERRUPTED_ROUTE))
            vm.clear()
        }
}
