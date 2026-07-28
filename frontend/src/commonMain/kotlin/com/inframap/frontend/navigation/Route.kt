package com.inframap.frontend.navigation

sealed interface Route {
    data object Splash : Route

    data object Login : Route

    data object Onboarding : Route

    data object Dashboard : Route

    data object Devices : Route

    data object Staging : Route

    data object Subnets : Route

    data object Topology : Route
}
