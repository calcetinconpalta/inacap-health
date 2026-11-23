package com.inacap.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.ui.screens.onboarding.OnboardingScreen
import com.inacap.health.ui.screens.perfil.PerfilViewModel
import com.inacap.health.ui.theme.INACAPHealthTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Aceptar todo» marca las tres casillas de los términos (y desmarcarlo las quita). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1400dp")
class AceptarTodoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<HealthApp>()

    @Before fun init() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
    }

    @Test fun aceptarTodo_habilitaComenzar() {
        val vm = PerfilViewModel(app)
        compose.setContent { INACAPHealthTheme { OnboardingScreen(vm, {}, {}) } }
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("Comenzar").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Aceptar todo").performScrollTo().performClick()
        compose.onNodeWithText("Comenzar").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Aceptar todo").performScrollTo().performClick()
        compose.onNodeWithText("Comenzar").performScrollTo().assertIsNotEnabled()
    }
}
