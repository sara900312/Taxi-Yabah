package com.yabah.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yabah.app.AppContainer
import com.yabah.app.feature.auth.SignInScreen
import com.yabah.app.feature.auth.SignInViewModel
import com.yabah.app.feature.home.PassengerHomeScreen
import com.yabah.app.feature.home.PassengerHomeViewModel
import com.yabah.app.feature.onboarding.OnboardingScreen
import com.yabah.app.feature.settings.CreditsScreen
import com.yabah.app.feature.settings.SettingsScreen
import com.yabah.app.feature.trip.TripScreen
import com.yabah.app.feature.trip.TripViewModel
import kotlinx.coroutines.launch

object Routes {
    const val Onboarding = "onboarding"
    const val SignIn = "signin"
    const val Home = "home"
    const val Trip = "trip/{tripId}"
    const val Settings = "settings"
    const val Credits = "credits"
    fun trip(id: String) = "trip/$id"
}

fun startRoute(c: AppContainer): String = when {
    !c.prefs.onboardingSeen -> Routes.Onboarding
    c.auth.session.value != null -> Routes.Home
    c.prefs.demoMode -> Routes.Home
    else -> Routes.SignIn
}

private fun NavHostController.resetTo(route: String) = navigate(route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }

@Composable
fun YabahNavHost(container: AppContainer, nav: NavHostController = rememberNavController(), start: String = startRoute(container)) {
    val scope = rememberCoroutineScope()
    val toSignIn = { container.auth.clearLocal(); nav.resetTo(Routes.SignIn) }
    NavHost(nav, startDestination = start) {
        composable(Routes.Onboarding) {
            OnboardingScreen(onFinished = {
                container.prefs.onboardingSeen = true
                nav.resetTo(if (container.auth.session.value != null || container.prefs.demoMode) Routes.Home else Routes.SignIn)
            })
        }
        composable(Routes.SignIn) {
            val vm = viewModel { SignInViewModel(container.auth, container.config.phoneOtpEnabled) }
            SignInScreen(
                vm,
                onSignedIn = { container.prefs.demoMode = false; nav.resetTo(Routes.Home) },
                onTryDemo = { container.prefs.demoMode = true; nav.resetTo(Routes.Home) },
            )
        }
        composable(Routes.Home) {
            val vm = viewModel {
                PassengerHomeViewModel(container.rideRepository(), container.places, container.serviceAreas, container.location, container.connectivity)
            }
            PassengerHomeScreen(
                vm,
                onOpenTrip = { id -> nav.navigate(Routes.trip(id)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.Settings) },
                onSessionExpired = toSignIn,
            )
        }
        composable(Routes.Trip, arguments = listOf(navArgument("tripId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("tripId").orEmpty()
            val vm = viewModel { TripViewModel(id, container.rideRepository()) }
            TripScreen(vm, onDone = { nav.resetTo(Routes.Home) }, onSessionExpired = toSignIn)
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onSignedOut = { nav.resetTo(Routes.SignIn) },
                onCredits = { nav.navigate(Routes.Credits) },
                onSignOut = { container.prefs.demoMode = false; scope.launch { container.auth.signOut() } },
            )
        }
        composable(Routes.Credits) { CreditsScreen(onBack = { nav.popBackStack() }) }
    }
}
