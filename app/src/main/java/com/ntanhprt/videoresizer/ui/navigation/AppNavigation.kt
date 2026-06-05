package com.ntanhprt.videoresizer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ntanhprt.videoresizer.ui.browse.BrowseScreen
import com.ntanhprt.videoresizer.ui.config.ConfigScreen
import com.ntanhprt.videoresizer.ui.progress.ProgressScreen
import com.ntanhprt.videoresizer.ui.result.ResultScreen

object Routes {
    const val BROWSE = "browse"
    const val CONFIG = "config/{selectedIds}"
    const val PROGRESS = "progress/{workId}"
    const val RESULT = "result/{workId}"

    fun config(selectedIds: String) = "config/$selectedIds"
    fun progress(workId: String) = "progress/$workId"
    fun result(workId: String) = "result/$workId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.BROWSE) {
        composable(Routes.BROWSE) {
            BrowseScreen(onNavigateToConfig = { ids ->
                navController.navigate(Routes.config(ids))
            })
        }
        composable(
            Routes.CONFIG,
            arguments = listOf(navArgument("selectedIds") { type = NavType.StringType })
        ) { back ->
            val ids = back.arguments?.getString("selectedIds") ?: ""
            ConfigScreen(
                selectedVideoIds = ids,
                onNavigateToProgress = { workId ->
                    navController.navigate(Routes.progress(workId)) {
                        popUpTo(Routes.BROWSE)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.PROGRESS,
            arguments = listOf(navArgument("workId") { type = NavType.StringType })
        ) { back ->
            val workId = back.arguments?.getString("workId") ?: ""
            ProgressScreen(
                workId = workId,
                onDone = { navController.navigate(Routes.result(workId)) { popUpTo(Routes.BROWSE) } }
            )
        }
        composable(
            Routes.RESULT,
            arguments = listOf(navArgument("workId") { type = NavType.StringType })
        ) { back ->
            val workId = back.arguments?.getString("workId") ?: ""
            ResultScreen(
                workId = workId,
                onProcessMore = { navController.navigate(Routes.BROWSE) { popUpTo(Routes.BROWSE) { inclusive = true } } },
                onDone = { navController.navigate(Routes.BROWSE) { popUpTo(Routes.BROWSE) { inclusive = true } } }
            )
        }
    }
}
