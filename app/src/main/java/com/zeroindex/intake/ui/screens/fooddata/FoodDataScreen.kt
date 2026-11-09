package com.zeroindex.intake.ui.screens.fooddata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.foodref.FoodRefInfo
import com.zeroindex.intake.data.keys.ApiKeyState
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.settings.FoodDataPrefs
import com.zeroindex.intake.data.settings.FoodDataSettings
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.SwitchRow
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FoodDataUiState(
    val settings: FoodDataSettings = FoodDataSettings(),
    val keys: ApiKeyState = ApiKeyState(),
)

@HiltViewModel
class FoodDataViewModel @Inject constructor(
    private val prefs: FoodDataPrefs,
    private val keys: ApiKeyStore,
) : ViewModel() {

    val uiState: StateFlow<FoodDataUiState> = combine(prefs.settings, keys.state) { settings, keyState ->
        FoodDataUiState(settings, keyState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDataUiState())

    fun setOffSearch(enabled: Boolean) {
        viewModelScope.launch { prefs.setOffSearch(enabled) }
    }

    fun setOffContribute(enabled: Boolean) {
        viewModelScope.launch { prefs.setOffContribute(enabled) }
    }

    fun saveCredentials(username: String, password: String) = keys.setOffCredentials(username, password)

    fun clearCredentials() = keys.setOffCredentials(null, null)
}

/**
 * Food database settings and credits (docs/FOOD-DATA.md): the Open Food Facts search
 * switch, the opt-in contribution with the user's own account, and the licence notices
 * for both data sources.
 */
@Composable
fun FoodDataScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FoodDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var username by remember(state.keys.offUsername) { mutableStateOf(state.keys.offUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }
    val emailTyped = '@' in username

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SubHead(
                "Food database & credits",
                "Search works offline with no key. Online sources are optional.",
            )
        }

        item {
            SectionCard("Searching packaged foods") {
                SwitchRow(
                    title = "Search Open Food Facts",
                    subtitle = "While you type in Log food. Sends only the words you typed, " +
                        "and only when you are online.",
                    on = state.settings.offSearch,
                    onToggle = { viewModel.setOffSearch(!state.settings.offSearch) },
                    onLabel = "On",
                    offLabel = "Off",
                )
            }
        }

        item {
            SectionCard("Contribute to Open Food Facts") {
                SwitchRow(
                    title = "Allow contributing",
                    subtitle = "Adds a Contribute button to foods you made that have a barcode. " +
                        "Nothing is ever sent automatically: you see exactly what will be sent " +
                        "and confirm each time.",
                    on = state.settings.offContribute,
                    onToggle = { viewModel.setOffContribute(!state.settings.offContribute) },
                    onLabel = "On",
                    offLabel = "Off",
                )
                LabeledField(
                    label = "Open Food Facts username",
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Your username, not your email",
                    isError = emailTyped,
                    supporting = if (emailTyped) "Use your username. An email address will not work." else null,
                )
                LabeledField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = if (state.keys.hasOffCredentials) "Saved. Type to replace it" else "Your account password",
                    secret = true,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.saveCredentials(username, password)
                            password = ""
                        },
                        enabled = username.isNotBlank() && !emailTyped && password.isNotEmpty(),
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Save") }
                    OutlinedButton(
                        onClick = {
                            viewModel.clearCredentials()
                            username = ""
                            password = ""
                        },
                        enabled = state.keys.hasOffCredentials,
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Remove") }
                }
                SandNote(
                    "Your account is stored encrypted on this phone. It is sent only to " +
                        "openfoodfacts.org, inside the request body over HTTPS, when you confirm " +
                        "a contribution. Your contribution is public under the ODbL licence.",
                )
            }
        }

        item {
            SectionCard("Credits") {
                Text(
                    "Generic foods: ${FoodRefInfo.USDA_CREDIT}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    FoodRefInfo.OFF_CREDIT,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Weather for the water target: data by Open-Meteo.com, licensed CC BY 4.0, only if you turn it on.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
