package net.pokedex.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * The paths actually used, driven through a release-like build to record what ART should
 * compile ahead of time. The pager is the reason this exists: as installed, it missed the
 * 120 Hz frame under JIT (docs/architecture.md §8).
 *
 * Every selector is text or a content description the app already exposes for TalkBack.
 * No test tags: this module must not need feature code to change. If a label changes and
 * a selector stops matching, the wait below fails loudly rather than recording a profile
 * of the wrong screen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = targetPackage(),
        // No startup profile: it decides dex layout for cold start, and this journey is
        // mostly paging and search. A copy of all of it there would buy nothing.
        includeInStartupProfile = false,
    ) {
        // 1. Cold start to the box view. The build under test is a fresh install with no
        // records, so the first launch offers a restore; turn it down, or the sheet covers
        // everything below. Later iterations do not see it.
        pressHome()
        startActivityAndWait()
        device.wait(Until.findObject(By.text(START_FRESH)), OFFER_TIMEOUT_MS)?.click()
        await(BOX_COUNTER)

        // The app reopens on the last box it showed, so reach box 1 through the sheet.
        // That is also journey 5, from the near end.
        jumpViaAllBoxes { rows -> rows.first() }

        // 2. Page through, both directions.
        repeat(PAGES) { swipePager(forward = true) }
        repeat(PAGES) { swipePager(forward = false) }

        // 3. A slot's detail and back, which includes the shared element. A tap on a pager
        // that is still settling stops the fling instead of opening the slot, so wait until
        // it is back on the first box.
        await(By.text(FIRST_BOX))
        onScreen(SLOT).click()
        await(BACK_BUTTON)
        device.pressBack()
        await(BOX_COUNTER)

        // 4. Search, type a name, open a result, back out of both.
        await(By.text(SEARCH_HINT)).click()
        await(By.clazz(EDIT_TEXT)).text = SEARCH_QUERY
        await(SEARCH_RESULT).click()
        await(BACK_BUTTON)
        device.pressBack()
        await(By.desc(CLOSE_SEARCH)).click()
        await(BOX_COUNTER)

        // 5. Catch and record (M3): tick a slot, open the catch sheet, cancel it, untick.
        // The build under test is uninstalled afterwards, so nothing it records survives.
        await(By.text(FIRST_BOX))
        onScreen(SLOT).click()
        await(By.text(MARK_CAUGHT)).click()
        await(By.text(WHERE_CAUGHT)).click()
        await(By.text(CATCH_DETAILS))
        await(By.text(CANCEL)).click()
        await(By.text(CAUGHT)).click()
        device.pressBack()
        await(BOX_COUNTER)

        // 6. Progress, scrolled to the bottom, and back.
        onScreen(PROGRESS_ENTRY).click()
        await(By.text(PROGRESS_TITLE))
        device.findObject(By.scrollable(true))?.let { list -> repeat(SHEET_FLINGS) { list.fling(Direction.DOWN) } }
        device.pressBack()
        await(BOX_COUNTER)

        // 7. Settings and back.
        await(By.desc(SETTINGS)).click()
        await(By.text(BACKUPS))
        device.pressBack()
        await(BOX_COUNTER)

        // 8. Hunting (M4): choose a game, walk the hunt list, open a hunt's slot and its
        // per-game section, then un-choose the game so every iteration starts the same way.
        setMyGame()
        await(By.text(HUNT_NEXT)).click()
        await(HUNT_ROW)
        flingList(Direction.DOWN)
        flingList(Direction.UP)
        await(HUNT_ROW).click()
        await(BACK_BUTTON)
        flingList(Direction.DOWN)
        device.pressBack()
        await(HUNT_ROW)
        device.pressBack()
        await(BOX_COUNTER)
        setMyGame()

        // 9. The "All boxes" sheet again, to the far end.
        jumpViaAllBoxes { rows ->
            val list = device.findObject(By.scrollable(true))
            repeat(SHEET_FLINGS) { list?.fling(Direction.DOWN) }
            sheetRows().last()
        }
    }

    /** Settings, My games, flip the game the journey hunts in, and back to the boxes. */
    private fun MacrobenchmarkScope.setMyGame() {
        await(By.desc(SETTINGS)).click()
        await(By.text(MY_GAMES)).click()
        await(By.text(MY_GAMES_HEADING))
        // Legends Arceus is a pair of one, so its section heading and its switch share the
        // name. The switch is the second.
        flingList(Direction.DOWN)
        await(By.text(HUNT_GAME))
        device.findObjects(By.text(HUNT_GAME)).last().click()
        device.pressBack()
        await(By.text(BACKUPS))
        device.pressBack()
        await(BOX_COUNTER)
    }

    /**
     * Flings whatever scrolls, finding it again each time. A lazy list that is still taking
     * rows replaces its node under a held reference, and the fling then throws.
     */
    private fun MacrobenchmarkScope.flingList(direction: Direction) {
        repeat(SHEET_FLINGS) {
            try {
                device.findObject(By.scrollable(true))?.fling(direction)
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
    }

    private fun MacrobenchmarkScope.jumpViaAllBoxes(pick: (List<UiObject2>) -> UiObject2) {
        await(By.text(ALL_BOXES)).click()
        pick(sheetRows()).click()
        await(BOX_COUNTER)
        device.waitForIdle()
    }

    /**
     * The box header on the grid reads like a sheet row ("Kanto 1: 0 of 30 caught, …"), so
     * one match proves nothing. The open sheet lists several; wait for that.
     */
    private fun MacrobenchmarkScope.sheetRows(): List<UiObject2> {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val rows = device.findObjects(SHEET_ROW)
            if (rows.size >= MIN_SHEET_ROWS) return rows
            device.waitForIdle()
        }
        error("The All boxes sheet did not open. A label the journey relies on has changed.")
    }

    private fun MacrobenchmarkScope.swipePager(forward: Boolean) {
        val y = device.displayHeight * PAGER_Y
        val left = (device.displayWidth * EDGE).toInt()
        val right = (device.displayWidth * (1 - EDGE)).toInt()
        if (forward) {
            device.swipe(right, y.toInt(), left, y.toInt(), SWIPE_STEPS)
        } else {
            device.swipe(left, y.toInt(), right, y.toInt(), SWIPE_STEPS)
        }
        // The same gap as the measured protocol (tools/perf/measure-pager.sh), so the
        // fling settles before the next swipe or tap.
        Thread.sleep(SWIPE_GAP_MS)
    }

    /**
     * The build under test (net.pokedex.profiling), as the plugin passes it, and never the
     * installed release: the run uninstalls what it tested, and the release holds the real
     * catch records. No fallback on purpose. This module instruments itself, so the target
     * context is the test APK and not the app.
     */
    private fun targetPackage(): String =
        checkNotNull(InstrumentationRegistry.getArguments().getString(TARGET_PACKAGE_ARG)) {
            "$TARGET_PACKAGE_ARG missing. Run through ./gradlew :app:generateBaselineProfile."
        }

    /** The pager keeps neighbouring pages composed, so the first match can be off screen. */
    private fun MacrobenchmarkScope.onScreen(selector: BySelector): UiObject2 {
        await(selector)
        return device.findObjects(selector).first { it.visibleCenter.x in 0 until device.displayWidth }
    }

    private fun MacrobenchmarkScope.await(selector: BySelector): UiObject2 =
        checkNotNull(device.wait(Until.findObject(selector), TIMEOUT_MS)) {
            "Nothing matched $selector. A label the journey relies on has changed."
        }
}

private const val TARGET_PACKAGE_ARG = "androidx.benchmark.targetPackageName"

private const val PAGES = 22
private const val SHEET_FLINGS = 4
private const val MIN_SHEET_ROWS = 3
private const val TIMEOUT_MS = 5_000L

/** Mid-grid on a portrait phone, clear of the header above and the box switcher below. */
private const val PAGER_Y = 0.4f
private const val EDGE = 0.15f

/** About 180 ms at uiautomator's 5 ms per step: the same speed as the measured swipes. */
private const val SWIPE_STEPS = 36
private const val SWIPE_GAP_MS = 500L

private const val SEARCH_HINT = "Name or dex number"
private const val SEARCH_QUERY = "pika"
private const val EDIT_TEXT = "android.widget.EditText"
private const val CLOSE_SEARCH = "Close search"
private const val ALL_BOXES = "All boxes"
private const val START_FRESH = "Start fresh"
private const val OFFER_TIMEOUT_MS = 2_000L
private const val MARK_CAUGHT = "Mark caught"
private const val CAUGHT = "Caught"
private const val WHERE_CAUGHT = "Where was it caught?"
private const val CATCH_DETAILS = "Catch details"
private const val CANCEL = "Cancel"
private const val PROGRESS_TITLE = "Progress"
private const val SETTINGS = "Settings and backups"
private const val BACKUPS = "Backups"
private const val MY_GAMES = "My games"
private const val MY_GAMES_HEADING = "Games you own and play"
private const val HUNT_GAME = "Legends Arceus"
private const val HUNT_NEXT = "Hunt next"

/** A hunt row: a needed slot, spoken with its reasons. */
private val HUNT_ROW = By.desc(Pattern.compile(".+, not yet caught\\. .*Legends Arceus.*"))

/** The headline readout, which opens Progress. Its sentence starts with the label. */
private val PROGRESS_ENTRY = By.desc(Pattern.compile("shiny: .*"))

private val BOX_COUNTER = By.text(Pattern.compile("Box \\d+ of \\d+"))
private const val FIRST_BOX = "Box 1 of 52"
private val BACK_BUTTON = By.desc("Back")

/**
 * A grid tile, whatever its state. Tiles are buttons; the detail screen's hero carries the
 * same description but is not one. Empty slots say "Empty slot".
 */
private val SLOT = By.clazz("android.widget.Button")
    .desc(Pattern.compile(".+, (shiny caught|not yet caught|shiny locked.*)"))
private val SEARCH_RESULT = By.desc(Pattern.compile("Pikachu, .*"))

/** "Kanto 1: 20 of 30 caught, 10 to go". The sheet's own "shiny: …" summary is not a box. */
private val SHEET_ROW = By.desc(Pattern.compile("(?!shiny:|Whole dex:).+: \\d+ of \\d+ caught.*"))
