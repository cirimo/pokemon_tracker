package net.pokedex.designsystem

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import net.pokedex.designsystem.theme.DarkCase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The launch background must be the same colour as the theme it stands in for.
 *
 * `windowBackground` and the adaptive icon background are read by the platform before any
 * Compose code runs, so that one value has to exist as an Android resource. Everything else
 * in the system lives in [net.pokedex.designsystem.theme.PokedexColors].
 *
 * A token with two homes drifts, and this one drifts *invisibly*: the only symptom is a
 * flash of slightly-wrong colour on cold start, which nobody notices until they do. So the
 * resource lives in :design-system next to the token it mirrors, and this asserts they are
 * still the same colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchColorTest {

    @Test
    fun launchBackgroundMatchesTheDarkCase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resource = Color(context.getColor(R.color.pokedex_case_black))
        assertWithMessage(
            "@color/pokedex_case_black is the cold-start window background and the adaptive " +
                "icon background. It must equal PokedexColors.case for the dark theme, or the " +
                "app flashes the wrong colour before Compose draws.",
        ).that(resource).isEqualTo(DarkCase.case)
    }
}
