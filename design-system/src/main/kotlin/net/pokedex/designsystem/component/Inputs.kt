package net.pokedex.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * Free text and dates, for the catch sheet.
 *
 * Both wrap M3 for the same reason SearchField does: text editing and date picking carry
 * behaviour (IME, selection handles, accessibility, locale calendars) that is not worth
 * rebuilding. What changes is colour. M3 draws the cursor, the selected day and "today" in
 * `primary`, which is gold in this theme, and a date is not a shiny.
 */

/** A multi-line note. Grows with its text; the sheet holding it scrolls. */
@Composable
fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    val colors = PokedexTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = PokedexTheme.dimens.touchTargetMin),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        shape = PokedexShapes.field,
        minLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.rimFocus,
            unfocusedBorderColor = colors.onCaseMuted,
            focusedContainerColor = colors.caseSurface,
            unfocusedContainerColor = colors.caseSurface,
            focusedTextColor = colors.onCase,
            unfocusedTextColor = colors.onCase,
            cursorColor = colors.onCase,
            focusedLabelColor = colors.onCase,
            unfocusedLabelColor = colors.onCaseMuted,
            focusedPlaceholderColor = colors.onCaseMuted,
            unfocusedPlaceholderColor = colors.onCaseMuted,
        ),
    )
}

/**
 * Picks a day, never one in the future: a catch cannot have happened tomorrow.
 *
 * Millis in and out are UTC midnight, which is what M3's picker speaks. Converting to and
 * from a local instant is the caller's job, because only the caller knows what time of day
 * a record should keep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexDatePickerDialog(
    initialUtcMillis: Long?,
    todayUtcMillis: Long,
    onPick: (utcMillis: Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = PokedexTheme.colors
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis ?: todayUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        shape = PokedexShapes.dialog,
        colors = pickerColors(),
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onPick) ?: onDismissRequest() }) {
                Text("Set date", style = PokedexTheme.text.badgeLabel, color = colors.onCase)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", style = PokedexTheme.text.badgeLabel, color = colors.onCaseMuted)
            }
        },
    ) {
        PokedexDatePicker(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PokedexDatePicker(state: DatePickerState) {
    // No mode toggle: typing a date brings M3's own text field back, gold cursor and all,
    // and a calendar is the faster way to say "last Tuesday" anyway.
    DatePicker(state = state, colors = pickerColors(), showModeToggle = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pickerColors(): DatePickerColors {
    val colors = PokedexTheme.colors
    return DatePickerDefaults.colors(
        containerColor = colors.caseSurfaceHigh,
        titleContentColor = colors.onCaseMuted,
        headlineContentColor = colors.onCase,
        weekdayContentColor = colors.onCaseMuted,
        subheadContentColor = colors.onCaseMuted,
        navigationContentColor = colors.onCase,
        yearContentColor = colors.onCase,
        currentYearContentColor = colors.onCase,
        selectedYearContentColor = colors.case,
        selectedYearContainerColor = colors.onCase,
        dayContentColor = colors.onCase,
        disabledDayContentColor = colors.onCaseMuted,
        selectedDayContentColor = colors.case,
        selectedDayContainerColor = colors.onCase,
        todayContentColor = colors.onCase,
        todayDateBorderColor = colors.onCaseMuted,
        dividerColor = colors.rim,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd)) {
        NoteField(value = "", onValueChange = {}, label = "Notes", placeholder = "Method, resets, anything")
        NoteField(value = "Sandwich, 412 resets.\nKept the Adamant one.", onValueChange = {}, label = "Notes")
        PokedexDatePicker(rememberDatePickerState(initialSelectedDateMillis = SAMPLE_DAY_UTC))
    }
}

/** 2026-03-04, a fixed day so the screenshot does not change with the calendar. */
private const val SAMPLE_DAY_UTC = 1_772_582_400_000L

@Preview(name = "Inputs dark", widthDp = 380, heightDp = 900)
@Composable
private fun InputsDarkPreview() {
    PokedexTheme(darkTheme = true) { InputSamples() }
}

@Preview(name = "Inputs light", widthDp = 380, heightDp = 900)
@Composable
private fun InputsLightPreview() {
    PokedexTheme(darkTheme = false) { InputSamples() }
}

@Preview(name = "Inputs 200%", widthDp = 380, heightDp = 1400, fontScale = 2f)
@Composable
private fun InputsLargeFontPreview() {
    PokedexTheme(darkTheme = true) { InputSamples() }
}
