package com.narro.app.feature

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndIconResourceTest {
    @Test
    fun launcherIconStaysInsideGoogleAdaptiveIconSafeZone() {
        val foreground = projectFile("src/main/res/drawable/ic_narro_foreground.xml").readText()
        val renderedLogoWidthDp = (87f - 21f) * 0.729f

        assertTrue(foreground.contains("""android:viewportWidth="108""""))
        assertTrue(foreground.contains("""android:viewportHeight="108""""))
        assertTrue(foreground.contains("""android:scaleX="0.729""""))
        assertTrue(foreground.contains("""android:scaleY="0.729""""))
        assertTrue(foreground.contains("M24,49C24,32 37,22 54,22C71,22 84,32 84,49"))
        assertTrue(foreground.contains("M35,36H46L64,64V36H74V86H64L46,58V86H35Z"))
        assertTrue(renderedLogoWidthDp in 48f..66f)
    }

    @Test
    fun lockScreenUsesThemeBackgroundAndNightSystemBars() {
        val appSource = projectFile(
            "src/main/java/com/narro/app/feature/NarroApp.kt",
        ).readText()
        val nightTheme = projectFile("src/main/res/values-night/themes.xml").readText()

        assertTrue(appSource.contains(".background(MaterialTheme.colorScheme.background)"))
        assertTrue(appSource.contains("containerColor = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertTrue(appSource.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(appSource.contains("MaterialTheme.colorScheme.outline"))
        assertTrue(nightTheme.contains("""name="android:windowLightStatusBar">false"""))
        assertTrue(nightTheme.contains("""name="android:navigationBarColor">#FF101413"""))
        assertTrue(nightTheme.contains("""name="android:statusBarColor">#FF101413"""))
    }

    private fun projectFile(relativePath: String): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: error("user.dir is unavailable"))
        return sequenceOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        ).first { it.isFile }
    }
}
