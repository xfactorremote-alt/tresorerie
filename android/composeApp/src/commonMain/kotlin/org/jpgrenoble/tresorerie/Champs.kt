package org.jpgrenoble.tresorerie

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.datetime.LocalDate

/**
 * Champ de date commun (équivalent de <input type="date"> du site) : on peut toujours taper JJ/MM/AAAA,
 * et l'icône calendrier ouvre le sélecteur. La valeur reste une chaîne JJ/MM/AAAA, lue par dateDepuisFr :
 * les écrans gardent leur logique et leurs contrôles (isError) inchangés.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChampDate(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = { Text("JJ/MM/AAAA") },
    @Suppress("UNUSED_PARAMETER") singleLine: Boolean = true, isError: Boolean = false,
) {
    var ouvert by remember { mutableStateOf(false) }
    OutlinedTextField(value, onValueChange, modifier = modifier, label = label, placeholder = placeholder, singleLine = true, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = { IconButton(onClick = { ouvert = true }) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Choisir dans le calendrier") } })
    if (ouvert) {
        val depart = (dateDepuisFr(value) ?: aujourdhui()).toEpochDays().toLong() * 86_400_000L
        val etat = rememberDatePickerState(initialSelectedDateMillis = depart)
        DatePickerDialog(onDismissRequest = { ouvert = false },
            confirmButton = { TextButton(onClick = {
                etat.selectedDateMillis?.let { ms -> onValueChange(dateFr(LocalDate.fromEpochDays((ms / 86_400_000L).toInt()).toString())) }
                ouvert = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { ouvert = false }) { Text("Annuler") } },
        ) { DatePicker(etat) }
    }
}
