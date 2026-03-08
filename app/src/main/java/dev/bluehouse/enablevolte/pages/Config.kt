package dev.bluehouse.enablevolte.pages

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Build.VERSION_CODES
import android.telephony.CarrierConfigManager
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.LocalSubscriptionActionGate
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.asyncTry
import dev.bluehouse.enablevolte.components.BooleanPropertyView
import dev.bluehouse.enablevolte.components.ClickablePropertyView
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.InfiniteLoadingDialog
import dev.bluehouse.enablevolte.components.RadioSelectPropertyView
import dev.bluehouse.enablevolte.components.UserAgentPropertyView
import dev.bluehouse.enablevolte.configPersistent
import dev.bluehouse.enablevolte.rememberSubscriptionActionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ConfigScreenState(
    val carrierName: String?,
    val simSlotIndex: Int,
    val volteSupportedBySystem: Boolean,
    val volteSupportedByUser: Boolean,
    val volteSupportedByDevice: Boolean,
    val volteSupportedByCarrier: Boolean,
    val volteGbaSatisfied: Boolean,
    val volteUiEditable: Boolean,
    val ltePlusEnabledByCarrier: Boolean,
    val volteTtySatisfied: Boolean,
    val volteProvisioningSatisfied: Boolean,
    val volteEnabled: Boolean,
    val voNREnabled: Boolean,
    val crossSIMEnabled: Boolean,
    val voWiFiEnabled: Boolean,
    val voWiFiEnabledWhileRoaming: Boolean,
    val showIMSinSIMInfo: Boolean,
    val allowAddingAPNs: Boolean,
    val showVoWifiMode: Boolean,
    val showVoWifiRoamingMode: Boolean,
    val wfcSpnFormatIndex: Int,
    val showVoWifiIcon: Boolean,
    val alwaysDataRATIcon: Boolean,
    val supportWfcWifiOnly: Boolean,
    val vtEnabled: Boolean,
    val ssOverUtEnabled: Boolean,
    val ssOverCDMAEnabled: Boolean,
    val show4GForLteEnabled: Boolean,
    val hideEnhancedDataIconEnabled: Boolean,
    val configuredUserAgent: String,
)

private fun readConfigScreenState(moder: SubscriptionModer): ConfigScreenState =
    ConfigScreenState(
        carrierName = moder.carrierName,
        simSlotIndex = moder.simSlotIndex,
        volteSupportedBySystem = moder.isVolteSupportedBySystem,
        volteSupportedByUser = moder.isVolteSupportedByUser,
        volteSupportedByDevice = moder.isVolteSupportedByDevice,
        volteSupportedByCarrier = moder.isVolteSupportedByCarrier,
        volteGbaSatisfied = moder.isGbaValid,
        volteUiEditable = moder.isVolteUiEditable,
        ltePlusEnabledByCarrier = moder.isLtePlusEnabledByCarrier,
        volteTtySatisfied = moder.isNonTtyOrTtyOnVolteEnabled,
        volteProvisioningSatisfied = Build.VERSION.SDK_INT <= VERSION_CODES.Q || moder.isVolteProvisioned,
        volteEnabled = moder.isVolteEnabled,
        voNREnabled = Build.VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE && moder.isVoNrConfigEnabled,
        crossSIMEnabled = moder.isCrossSIMConfigEnabled,
        voWiFiEnabled = moder.isVoWifiConfigEnabled,
        voWiFiEnabledWhileRoaming = moder.isVoWifiWhileRoamingEnabled,
        showIMSinSIMInfo = Build.VERSION.SDK_INT >= VERSION_CODES.R && moder.showIMSinSIMInfo,
        allowAddingAPNs = moder.allowAddingAPNs,
        showVoWifiMode = Build.VERSION.SDK_INT >= VERSION_CODES.R && moder.showVoWifiMode,
        showVoWifiRoamingMode = Build.VERSION.SDK_INT >= VERSION_CODES.R && moder.showVoWifiRoamingMode,
        wfcSpnFormatIndex = moder.wfcSpnFormatIndex,
        showVoWifiIcon = moder.showVoWifiIcon,
        alwaysDataRATIcon = Build.VERSION.SDK_INT >= VERSION_CODES.R && moder.alwaysDataRATIcon,
        supportWfcWifiOnly = moder.supportWfcWifiOnly,
        vtEnabled = moder.isVtConfigEnabled,
        ssOverUtEnabled = moder.ssOverUtEnabled,
        ssOverCDMAEnabled = moder.ssOverCDMAEnabled,
        show4GForLteEnabled = Build.VERSION.SDK_INT >= VERSION_CODES.R && moder.isShow4GForLteEnabled,
        hideEnhancedDataIconEnabled = Build.VERSION.SDK_INT >= VERSION_CODES.R && moder.isHideEnhancedDataIconEnabled,
        configuredUserAgent = moder.userAgentConfig ?: "",
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun Config(
    navController: NavController,
    subId: Int,
    onInvalidAccess: () -> Unit,
) {
    val tag = "HomeActivity:Config"
    val context = LocalContext.current
    val moder = remember(context, subId) { SubscriptionModer(context, subId) }
    val actionGate =
        rememberSubscriptionActionGate(moder) {
            onInvalidAccess()
        }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var carrierName by rememberSaveable { mutableStateOf<String?>(null) }
    var simSlotIndex by rememberSaveable { mutableIntStateOf(0) }
    var volteSupportedBySystem by rememberSaveable { mutableStateOf(false) }
    var volteSupportedByUser by rememberSaveable { mutableStateOf(false) }
    var volteSupportedByDevice by rememberSaveable { mutableStateOf(false) }
    var volteSupportedByCarrier by rememberSaveable { mutableStateOf(false) }
    var volteGbaSatisfied by rememberSaveable { mutableStateOf(false) }
    var volteUiEditable by rememberSaveable { mutableStateOf(false) }
    var ltePlusEnabledByCarrier by rememberSaveable { mutableStateOf(false) }
    var volteTtySatisfied by rememberSaveable { mutableStateOf(false) }
    var volteProvisioningSatisfied by rememberSaveable { mutableStateOf(false) }
    var volteEnabled by rememberSaveable { mutableStateOf(false) }
    var voNREnabled by rememberSaveable { mutableStateOf(false) }
    var crossSIMEnabled by rememberSaveable { mutableStateOf(false) }
    var voWiFiEnabled by rememberSaveable { mutableStateOf(false) }
    var voWiFiEnabledWhileRoaming by rememberSaveable { mutableStateOf(false) }
    var showIMSinSIMInfo by rememberSaveable { mutableStateOf(false) }
    var allowAddingAPNs by rememberSaveable { mutableStateOf(false) }
    var showVoWifiMode by rememberSaveable { mutableStateOf(false) }
    var showVoWifiRoamingMode by rememberSaveable { mutableStateOf(false) }
    var wfcSpnFormatIndex by rememberSaveable { mutableIntStateOf(0) }
    var showVoWifiIcon by rememberSaveable { mutableStateOf(false) }
    var alwaysDataRATIcon by rememberSaveable { mutableStateOf(false) }
    var supportWfcWifiOnly by rememberSaveable { mutableStateOf(false) }
    var vtEnabled by rememberSaveable { mutableStateOf(false) }
    var ssOverUtEnabled by rememberSaveable { mutableStateOf(false) }
    var ssOverCDMAEnabled by rememberSaveable { mutableStateOf(false) }
    var show4GForLteEnabled by rememberSaveable { mutableStateOf(false) }
    var hideEnhancedDataIconEnabled by rememberSaveable { mutableStateOf(false) }
    var configuredUserAgent by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(true) }

    fun applyState(state: ConfigScreenState) {
        Log.d(tag, "applyState")
        carrierName = state.carrierName
        simSlotIndex = state.simSlotIndex
        volteSupportedBySystem = state.volteSupportedBySystem
        volteSupportedByUser = state.volteSupportedByUser
        volteSupportedByDevice = state.volteSupportedByDevice
        volteSupportedByCarrier = state.volteSupportedByCarrier
        volteGbaSatisfied = state.volteGbaSatisfied
        volteUiEditable = state.volteUiEditable
        ltePlusEnabledByCarrier = state.ltePlusEnabledByCarrier
        volteTtySatisfied = state.volteTtySatisfied
        volteProvisioningSatisfied = state.volteProvisioningSatisfied
        volteEnabled = state.volteEnabled
        voNREnabled = state.voNREnabled
        crossSIMEnabled = state.crossSIMEnabled
        voWiFiEnabled = state.voWiFiEnabled
        voWiFiEnabledWhileRoaming = state.voWiFiEnabledWhileRoaming
        showIMSinSIMInfo = state.showIMSinSIMInfo
        allowAddingAPNs = state.allowAddingAPNs
        showVoWifiMode = state.showVoWifiMode
        showVoWifiRoamingMode = state.showVoWifiRoamingMode
        wfcSpnFormatIndex = state.wfcSpnFormatIndex
        showVoWifiIcon = state.showVoWifiIcon
        alwaysDataRATIcon = state.alwaysDataRATIcon
        supportWfcWifiOnly = state.supportWfcWifiOnly
        vtEnabled = state.vtEnabled
        ssOverUtEnabled = state.ssOverUtEnabled
        ssOverCDMAEnabled = state.ssOverCDMAEnabled
        show4GForLteEnabled = state.show4GForLteEnabled
        hideEnhancedDataIconEnabled = state.hideEnhancedDataIconEnabled
        configuredUserAgent = state.configuredUserAgent
    }

    fun refreshFlagsOrRecover() {
        try {
            applyState(readConfigScreenState(moder))
        } catch (_: IllegalStateException) {
            onInvalidAccess()
        }
    }

    val volteConfigPersistent by remember(
        volteSupportedBySystem,
        volteSupportedByUser,
        volteSupportedByDevice,
        volteSupportedByCarrier,
        volteGbaSatisfied,
        volteUiEditable,
        ltePlusEnabledByCarrier,
        volteTtySatisfied,
        volteProvisioningSatisfied,
    ) {
        derivedStateOf {
            configPersistent ||
                (
                    volteSupportedBySystem ||
                        volteSupportedByUser ||
                        (volteSupportedByDevice && volteSupportedByCarrier && volteGbaSatisfied)
                ) &&
                (volteSupportedByUser || volteUiEditable || ltePlusEnabledByCarrier) &&
                volteTtySatisfied &&
                volteProvisioningSatisfied
        }
    }

    LaunchedEffect(moder) {
        loading = true
        if (subId >= 0) {
            try {
                val state =
                    withContext(Dispatchers.Default) {
                        readConfigScreenState(moder)
                    }
                applyState(state)
            } catch (_: IllegalStateException) {
            } finally {
                loading = false
            }
        } else {
            loading = false
        }
    }

    if (loading) {
        InfiniteLoadingDialog()
        return
    }

    val currentCarrierName = carrierName.orEmpty()

    CompositionLocalProvider(LocalSubscriptionActionGate provides actionGate) {
        Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
            BooleanPropertyView(
                label = stringResource(R.string.persist_config),
                toggled = configPersistent,
                enabled = false,
            ) {
                configPersistent = !configPersistent
            }
            BooleanPropertyView(
                label = stringResource(R.string.persist_volte_config),
                toggled = volteConfigPersistent,
            )
            BooleanPropertyView(label = stringResource(R.string.volte_supported_by_system), toggled = volteSupportedBySystem)
            BooleanPropertyView(label = stringResource(R.string.volte_supported_by_user), toggled = volteSupportedByUser)
            BooleanPropertyView(label = stringResource(R.string.volte_supported_by_device), toggled = volteSupportedByDevice)
            BooleanPropertyView(label = stringResource(R.string.volte_supported_by_carrier), toggled = volteSupportedByCarrier)
            BooleanPropertyView(label = stringResource(R.string.volte_gba_satisfied), toggled = volteGbaSatisfied)
            BooleanPropertyView(label = stringResource(R.string.volte_tty_satisfied), toggled = volteTtySatisfied)
            BooleanPropertyView(label = stringResource(R.string.volte_provisioning_satisfied), toggled = volteProvisioningSatisfied)

            HeaderText(text = stringResource(R.string.feature_toggles))
            BooleanPropertyView(
                label = stringResource(R.string.enable_volte),
                toggled = volteEnabled,
                enabled = !volteEnabled || volteSupportedByUser || volteUiEditable || ltePlusEnabledByCarrier,
            ) {
                asyncTry(scope) {
                    async {
                        if (!volteEnabled) {
                            if (volteConfigPersistent) {
                                moder.setVolteSupportedByUser(true)
                            }
                            moder.setGbaRequired(false)
                            moder.setVoLteProvisioned(true)
                        }
                        moder.setVolteEnabled(!volteEnabled)
                        moder.restartIMSRegistration()
                    }
                    ok {
                        refreshFlagsOrRecover()
                    }
                    error {
                        onInvalidAccess()
                    }
                }
            }
            BooleanPropertyView(
                label = stringResource(R.string.enable_vonr),
                toggled = voNREnabled,
                minSdk = VERSION_CODES.UPSIDE_DOWN_CAKE,
            ) {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    voNREnabled =
                        if (voNREnabled) {
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, false)
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, false)
                            false
                        } else {
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true)
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true)
                            moder.restartIMSRegistration()
                            true
                        }
                }
            }
            BooleanPropertyView(
                label = stringResource(R.string.enable_crosssim),
                toggled = crossSIMEnabled,
                minSdk = VERSION_CODES.TIRAMISU,
            ) {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    crossSIMEnabled =
                        if (crossSIMEnabled) {
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, false)
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, false)
                            false
                        } else {
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, true)
                            moder.updateCarrierConfig(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, true)
                            moder.restartIMSRegistration()
                            true
                        }
                }
            }
            BooleanPropertyView(label = stringResource(R.string.enable_vowifi), toggled = voWiFiEnabled) {
                voWiFiEnabled =
                    if (voWiFiEnabled) {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, false)
                        false
                    } else {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                        moder.restartIMSRegistration()
                        true
                    }
            }
            BooleanPropertyView(label = stringResource(R.string.enable_vowifi_while_roamed), toggled = voWiFiEnabledWhileRoaming) {
                voWiFiEnabledWhileRoaming =
                    if (voWiFiEnabledWhileRoaming) {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL, false)
                        false
                    } else {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL, true)
                        moder.restartIMSRegistration()
                        true
                    }
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                BooleanPropertyView(
                    label = stringResource(R.string.enable_ss_over_ut),
                    toggled = ssOverUtEnabled,
                ) {
                    ssOverUtEnabled =
                        if (ssOverUtEnabled) {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL,
                                false,
                            )
                            false
                        } else {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL,
                                true,
                            )
                            moder.restartIMSRegistration()
                            true
                        }
                }
            }
            BooleanPropertyView(label = stringResource(R.string.enable_ss_over_cdma), toggled = ssOverCDMAEnabled) {
                ssOverCDMAEnabled =
                    if (ssOverCDMAEnabled) {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL, false)
                        false
                    } else {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL, true)
                        moder.restartIMSRegistration()
                        true
                    }
            }
            BooleanPropertyView(label = stringResource(R.string.enable_video_calling_vt), toggled = vtEnabled) {
                vtEnabled =
                    if (vtEnabled) {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, false)
                        false
                    } else {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true)
                        moder.restartIMSRegistration()
                        true
                    }
            }
            BooleanPropertyView(label = stringResource(R.string.allow_adding_apns), toggled = allowAddingAPNs) {
                allowAddingAPNs =
                    if (allowAddingAPNs) {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL, false)
                        false
                    } else {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL, true)
                        true
                    }
            }

            HeaderText(text = stringResource(R.string.string_values))
            UserAgentPropertyView(label = stringResource(R.string.user_agent), value = configuredUserAgent) {
                moder.updateCarrierConfig(moder.KEY_IMS_USER_AGENT, it)
                configuredUserAgent = it
            }

            HeaderText(text = stringResource(R.string.cosmetic_toggles))
            if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
                BooleanPropertyView(
                    label = stringResource(R.string.show_vowifi_preference_in_settings),
                    toggled = showVoWifiMode,
                    minSdk = VERSION_CODES.R,
                ) {
                    showVoWifiMode =
                        if (showVoWifiMode) {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL,
                                false,
                            )
                            false
                        } else {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL,
                                true,
                            )
                            moder.restartIMSRegistration()
                            true
                        }
                }
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
                BooleanPropertyView(
                    label = stringResource(R.string.show_vowifi_roaming_preference_in_settings),
                    toggled = showVoWifiRoamingMode,
                    minSdk = VERSION_CODES.R,
                ) {
                    showVoWifiRoamingMode =
                        if (showVoWifiRoamingMode) {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL,
                                false,
                            )
                            false
                        } else {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL,
                                true,
                            )
                            moder.restartIMSRegistration()
                            true
                        }
                }
            }
            RadioSelectPropertyView(
                label = stringResource(R.string.wi_fi_calling_carrier_name_format),
                values =
                    arrayOf(
                        "%s".format(currentCarrierName),
                        "%s Wi-Fi Calling".format(currentCarrierName),
                        "WLAN Call",
                        "%s WLAN Call".format(currentCarrierName),
                        "%s Wi-Fi".format(currentCarrierName),
                        "WiFi Calling | %s".format(currentCarrierName),
                        "%s VoWifi".format(currentCarrierName),
                        "Wi-Fi Calling",
                        "Wi-Fi",
                        "WiFi Calling",
                        "VoWifi",
                        "%s WiFi Calling".format(currentCarrierName),
                        "WiFi Call",
                    ),
                selectedIndex = wfcSpnFormatIndex,
            ) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT, it)
                wfcSpnFormatIndex = it
            }
            BooleanPropertyView(label = stringResource(R.string.show_wifi_only_for_vowifi), toggled = supportWfcWifiOnly) {
                supportWfcWifiOnly =
                    if (supportWfcWifiOnly) {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, false)
                        false
                    } else {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true)
                        moder.restartIMSRegistration()
                        true
                    }
            }
            BooleanPropertyView(label = stringResource(R.string.show_vowifi_icon), toggled = showVoWifiIcon) {
                showVoWifiIcon =
                    if (showVoWifiIcon) {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, false)
                        false
                    } else {
                        moder.updateCarrierConfig(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true)
                        true
                    }
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
                BooleanPropertyView(
                    label = stringResource(R.string.always_show_data_icon),
                    toggled = alwaysDataRATIcon,
                    minSdk = VERSION_CODES.R,
                ) {
                    alwaysDataRATIcon =
                        if (alwaysDataRATIcon) {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL,
                                false,
                            )
                            false
                        } else {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL,
                                true,
                            )
                            true
                        }
                }
                BooleanPropertyView(
                    label = stringResource(R.string.show_4g_for_lte_data_icon),
                    toggled = show4GForLteEnabled,
                    minSdk = VERSION_CODES.R,
                ) {
                    show4GForLteEnabled =
                        if (show4GForLteEnabled) {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL,
                                false,
                            )
                            false
                        } else {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL,
                                true,
                            )
                            true
                        }
                }
                BooleanPropertyView(
                    label = stringResource(R.string.hide_enhanced_data_icon),
                    toggled = hideEnhancedDataIconEnabled,
                    minSdk = VERSION_CODES.R,
                ) {
                    hideEnhancedDataIconEnabled =
                        if (hideEnhancedDataIconEnabled) {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL,
                                false,
                            )
                            false
                        } else {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL,
                                true,
                            )
                            true
                        }
                }
                BooleanPropertyView(
                    label = stringResource(R.string.show_ims_status_in_sim_status),
                    toggled = showIMSinSIMInfo,
                    minSdk = VERSION_CODES.R,
                ) {
                    showIMSinSIMInfo =
                        if (showIMSinSIMInfo) {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL,
                                false,
                            )
                            false
                        } else {
                            moder.updateCarrierConfig(
                                CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL,
                                true,
                            )
                            true
                        }
                }
            }

            if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                val statusBarManager = context.getSystemService(StatusBarManager::class.java)

                HeaderText(text = stringResource(R.string.qstile))
                ClickablePropertyView(
                    label = stringResource(R.string.add_status_tile),
                    value = "",
                ) {
                    statusBarManager.requestAddTileService(
                        ComponentName(
                            context,
                            // TODO: what happens if someone tries to use this feature from a triple(or even dual)-SIM phone?
                            Class.forName("dev.bluehouse.enablevolte.SIM${simSlotIndex + 1}IMSStatusQSTileService"),
                        ),
                        context.getString(R.string.qs_status_tile_title, (simSlotIndex + 1).toString()),
                        Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
                        {},
                        {},
                    )
                }
                ClickablePropertyView(
                    label = stringResource(R.string.add_toggle_tile),
                    value = "",
                ) {
                    statusBarManager.requestAddTileService(
                        ComponentName(
                            context,
                            Class.forName("dev.bluehouse.enablevolte.SIM${simSlotIndex + 1}VoLTEConfigToggleQSTileService"),
                        ),
                        context.getString(R.string.qs_toggle_tile_title, (simSlotIndex + 1).toString()),
                        Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
                        {},
                        {},
                    )
                }
            }

            HeaderText(text = stringResource(R.string.miscellaneous))
            ClickablePropertyView(
                label = stringResource(R.string.reset_all_settings),
                value = stringResource(R.string.reverts_to_carrier_default),
            ) {
                asyncTry(scope) {
                    before {
                        loading = true
                    }
                    async {
                        moder.clearCarrierConfig()
                    }
                    ok {
                        refreshFlagsOrRecover()
                    }
                    error {
                        onInvalidAccess()
                    }
                    always {
                        loading = false
                    }
                }
            }
            ClickablePropertyView(
                label = stringResource(R.string.expert_mode),
                value = "",
            ) {
                navController.navigate("config$subId/edit")
            }
            ClickablePropertyView(
                label = stringResource(R.string.dump_config),
                value = "",
            ) {
                navController.navigate("config$subId/dump")
            }
            ClickablePropertyView(
                label = stringResource(R.string.restart_ims_registration),
                value = "",
            ) {
                asyncTry(scope) {
                    before {
                        loading = true
                    }
                    async {
                        moder.restartIMSRegistration()
                    }
                    ok {
                        refreshFlagsOrRecover()
                    }
                    error {
                        onInvalidAccess()
                    }
                    always {
                        loading = false
                    }
                }
            }
        }
    }
}
