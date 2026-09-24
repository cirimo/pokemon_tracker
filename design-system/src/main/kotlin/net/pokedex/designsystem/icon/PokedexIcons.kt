package net.pokedex.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icon family, and the whole of it.
 *
 * ## The rules
 *
 * 1. **One family, one weight.** These are Material Symbols in the filled style, which is
 *    the set that ships inside `material-icons-core` -- about forty icons, already on the
 *    classpath, no extra dependency and no extra APK weight.
 * 2. **This object is the allowlist.** Features import from here, never from
 *    `androidx.compose.material.icons.*` directly, and never from
 *    `material-icons-extended` (a 20MB artifact that exists to be avoided). If a screen
 *    needs an icon that is not here, that is a design conversation, not an import.
 * 3. **Sizes come from `PokedexTheme.dimens`** -- `iconSm` 16dp, `iconMd` 20dp, `iconLg`
 *    24dp. Nothing else.
 * 4. **Directional icons use the AutoMirrored set**, so back and next flip in RTL without
 *    a per-screen decision. The app is English-only today; that is not a reason to ship
 *    arrows that would point the wrong way the moment it is not.
 * 5. **Icons never carry state alone.** An icon next to a label is fine; an icon that is
 *    the only difference between two states is not. The same rule the slot marks follow.
 *
 * The one deliberate absence is a "caught" icon. Caught is expressed by the gold rim, the
 * pip and the sprite arriving in colour -- adding a tick icon to that would be a fourth
 * signal for a state that already has three.
 */
object PokedexIcons {
    val Search: ImageVector = Icons.Filled.Search
    val ClearInput: ImageVector = Icons.Filled.Clear
    val Close: ImageVector = Icons.Filled.Close
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Check: ImageVector = Icons.Filled.Check
    val Add: ImageVector = Icons.Filled.Add

    /** Shiny-locked. The only place a lock appears. */
    val Locked: ImageVector = Icons.Filled.Lock

    val Favourite: ImageVector = Icons.Filled.Favorite
    val FavouriteOutline: ImageVector = Icons.Filled.FavoriteBorder

    /** Priority in the hunt queue, not "rating". */
    val Priority: ImageVector = Icons.Filled.Star

    val Sort: ImageVector = Icons.AutoMirrored.Filled.List
    val Filter: ImageVector = Icons.Filled.Settings
    val Info: ImageVector = Icons.Filled.Info
    val Error: ImageVector = Icons.Filled.Warning
    val Retry: ImageVector = Icons.Filled.Refresh

    /**
     * The same gear as [Filter], which the core set forces. They never share a screen state:
     * Settings sits on the box view's top bar, which search mode replaces.
     */
    val Settings: ImageVector = Icons.Filled.Settings
    val Edit: ImageVector = Icons.Filled.Edit
    val Delete: ImageVector = Icons.Filled.Delete
    val Date: ImageVector = Icons.Filled.DateRange

    val ChevronDown: ImageVector = Icons.Filled.KeyboardArrowDown
    val ChevronUp: ImageVector = Icons.Filled.KeyboardArrowUp
    val PreviousBox: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val NextBox: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
}
