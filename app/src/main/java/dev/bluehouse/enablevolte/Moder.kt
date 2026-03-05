package dev.bluehouse.enablevolte

import android.app.IActivityManager
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.net.IConnectivityManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.PersistableBundle
import android.os.RemoteException
import android.os.ServiceManager
import android.os.SystemProperties
import android.telecom.TelecomManager
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import android.telephony.ims.ImsException
import android.telephony.ims.ProvisioningManager
import android.telephony.ims.aidl.IImsConfig
import android.telephony.ims.feature.ImsFeature
import android.telephony.ims.feature.MmTelFeature
import android.telephony.ims.stub.ImsConfigImplBase
import android.telephony.ims.stub.ImsRegistrationImplBase
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.ims.ImsConfig
import com.android.internal.telecom.ITelecomService
import com.android.internal.telephony.ICarrierConfigLoader
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.WeakHashMap

object InterfaceCache {
    @PublishedApi
    internal object NULL_INTERFACE : IInterface {
        override fun asBinder(): IBinder? = null
    }

    @PublishedApi
    internal val nameCache = HashMap<String, IInterface>()

    @PublishedApi
    internal val binderCache = WeakHashMap<IBinder, IInterface>()
}

var configPersistent by mutableStateOf(false)

open class Moder {
    @Suppress("ktlint:standard:property-naming")
    val KEY_IMS_USER_AGENT = "ims.ims_user_agent_string"

    protected inline fun <reified T : IInterface> loadCachedInterface(crossinline interfaceLoader: () -> T): T =
        synchronized(InterfaceCache.nameCache) {
            InterfaceCache.nameCache.getOrPut(T::class.java.name, interfaceLoader) as T
        }

    protected inline fun <reified T : IInterface> loadCachedNullableInterface(crossinline interfaceLoader: () -> T?): T? =
        synchronized(InterfaceCache.nameCache) {
            val value = InterfaceCache.nameCache.getOrPut(T::class.java.name) {
                interfaceLoader() ?: InterfaceCache.NULL_INTERFACE
            }
            if (value === InterfaceCache.NULL_INTERFACE) null else value as T
        }

    protected inline fun <reified T : IInterface> IBinder.cache(crossinline asInterface: (IBinder) -> T): T =
        synchronized(InterfaceCache.binderCache) {
            InterfaceCache.binderCache.getOrPut(this) {
                asInterface(ShizukuBinderWrapper(this))
            } as T
        }

    protected val carrierConfigLoader: ICarrierConfigLoader
        get() =
            ICarrierConfigLoader.Stub.asInterface(
                ShizukuBinderWrapper(
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .carrierConfigServiceRegisterer
                            .get()
                    } catch (_: NoClassDefFoundError) {
                        ServiceManager.getService(Context.CARRIER_CONFIG_SERVICE)
                    }!!,
                ),
            )

    protected val telephony: ITelephony
        get() =
            ITelephony.Stub.asInterface(
                ShizukuBinderWrapper(
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .telephonyServiceRegisterer
                            .get()
                    } catch (_: NoClassDefFoundError) {
                        ServiceManager.getService(Context.TELEPHONY_SERVICE)
                    }!!,
                ),
            )

    protected val phoneSubInfo: IPhoneSubInfo?
        get() =
            try {
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .phoneSubServiceRegisterer
                    .get()
            } catch (_: NoClassDefFoundError) {
                ServiceManager.getService("iphonesubinfo")
            }?.let { IPhoneSubInfo.Stub.asInterface(ShizukuBinderWrapper(it)) }

    protected val sub: ISub
        get() =
            ISub.Stub.asInterface(
                ShizukuBinderWrapper(
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .subscriptionServiceRegisterer
                            .get()
                    } catch (_: NoClassDefFoundError) {
                        ServiceManager.getService("isub")
                    }!!,
                ),
            )

    protected val telecom: ITelecomService?
        get() =
            ServiceManager.getService(Context.TELECOM_SERVICE)
                ?.let { ITelecomService.Stub.asInterface(ShizukuBinderWrapper(it)) }

    protected val connectivity: IConnectivityManager?
        get() =
            ServiceManager.getService(Context.CONNECTIVITY_SERVICE)
                ?.let { IConnectivityManager.Stub.asInterface(ShizukuBinderWrapper(it)) }

    fun setAirplaneMode(enabled: Boolean) {
        val connectivity = this.loadCachedNullableInterface { connectivity } ?: return
        connectivity.setAirplaneMode(enabled)
    }
}

open class CarrierModer(
    private val context: Context,
) : Moder() {
    fun getActiveSubscriptionInfoForSimSlotIndex(index: Int): SubscriptionInfo? {
        val sub = this.loadCachedInterface { sub }
        return try {
            sub.getActiveSubscriptionInfoForSimSlotIndex(index, null, null)
        } catch (_: NoSuchMethodError) {
            val getActiveSubscriptionInfoForSimSlotIndexMethod =
                sub.javaClass.getMethod(
                    "getActiveSubscriptionInfoForSimSlotIndex",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
            (getActiveSubscriptionInfoForSimSlotIndexMethod.invoke(sub, index, null) as? SubscriptionInfo)
        }
    }

    val subscriptions: List<SubscriptionInfo>
        get() {
            val sub = this.loadCachedInterface { sub }
            return try {
                sub.getActiveSubscriptionInfoList(null, null, true) ?: emptyList()
            } catch (_: NoSuchMethodError) {
                null
            } ?: try {
                val getActiveSubscriptionInfoListMethod =
                    sub.javaClass.getMethod(
                        "getActiveSubscriptionInfoList",
                        String::class.java,
                        String::class.java,
                    )
                (getActiveSubscriptionInfoListMethod.invoke(sub, null, null) as? List<SubscriptionInfo>) ?: emptyList()
            } catch (_: NoSuchMethodException) {
                val getActiveSubscriptionInfoListMethod =
                    sub.javaClass.getMethod(
                        "getActiveSubscriptionInfoList",
                        String::class.java,
                    )
                (getActiveSubscriptionInfoListMethod.invoke(sub, null) as? List<SubscriptionInfo>) ?: emptyList()
            }
        }

    val defaultSubId: Int
        get() {
            val sub = this.loadCachedInterface { sub }
            return sub.defaultSubId
        }

    val isVolteSupportedByDevice: Boolean
        get() =
            try {
                val res = Resources.getSystem()
                val volteConfigId = res.getIdentifier("config_device_volte_available", "bool", "android")
                res.getBoolean(volteConfigId)
            } catch (e: Resources.NotFoundException) {
                Log.d(TAG, "getImsProvisionedBoolNoException", e)
                false
            }

    fun setImsRegistrationState(registered: Boolean) {
        val telephony = this.loadCachedInterface { telephony }
        telephony.setImsRegistrationState(registered)
    }
}

class SubscriptionModer(
    private val context: Context,
    val subscriptionId: Int,
) : Moder(context) {
    @Suppress("ktlint:standard:property-naming")
    private val TAG = "SubscriptionModer"

    protected val PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE = "persist.dbg.volte_avail_ovr"

    protected val KEYS_MMTEL_CAPABILITY =
        mapOf(
            MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE to CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
            MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO to CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
            MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT to CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_UT_INT_ARRAY,
            MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS to CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_SMS_INT_ARRAY,
            MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER to
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_CALL_COMPOSER_INT_ARRAY,
        )

    private fun overrideConfigDirectly(bundle: Bundle?) {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        val args = bundle?.let(::toPersistableBundle)

        try {
            iCclInstance.overrideConfig(subscriptionId, args, configPersistent)
        } catch (e: NoSuchMethodError) {
            val overrideConfigMethod =
                iCclInstance.javaClass.getMethod(
                    "overrideConfig",
                    Int::class.javaPrimitiveType,
                    PersistableBundle::class.java,
                )
            overrideConfigMethod.invoke(iCclInstance, subscriptionId, args)
            if (configPersistent) {
                throw e
            }
        }
    }

    private fun overrideConfigUsingBroker(bundle: Bundle?) {
        val am =
            IActivityManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE),
                ),
            )

        val arg =
            bundle ?: run {
                val empty = Bundle()
                empty.putBoolean("moder_clear", true)
                empty
            }
        arg.putInt("moder_subId", subscriptionId)

        am.startInstrumentation(
            ComponentName(context, Class.forName("dev.bluehouse.enablevolte.BrokerInstrumentation")),
            null,
            8,
            arg,
            null,
            UiAutomationConnection(),
            0,
            null,
        )
    }

    private fun overrideConfig(bundle: Bundle?) {
        val securityPatchDate =
            try {
                LocalDate.parse(Build.VERSION.SECURITY_PATCH)
            } catch (_: DateTimeParseException) {
                null
            }
        if (securityPatchDate == null || securityPatchDate.isBefore(LocalDate.of(2025, 10, 1))) {
            try {
                return this.overrideConfigDirectly(bundle)
            } catch (_: SecurityException) {
            } catch (_: NoSuchMethodError) {
            } catch (_: NoSuchMethodException) {
            }
            if (!configPersistent) {
                return
            }
        }
        this.overrideConfigUsingBroker(bundle)
    }

    private fun publishBundle(fn: (Bundle) -> Unit) {
        val overrideBundle = Bundle()
        fn(overrideBundle)
        this.overrideConfig(overrideBundle)
    }

    val isActiveSubId: Boolean
        get() {
            val sub = this.loadCachedInterface { sub }
            try {
                return sub.isActiveSubId(this.subscriptionId, null, null)
            } catch (_: NoSuchMethodError) {
            }
            return try {
                val isActiveSubIdMethod =
                    sub.javaClass.getMethod(
                        "isActiveSubId",
                        Int::class.javaPrimitiveType,
                        String::class.java,
                    )
                isActiveSubIdMethod.invoke(sub, subscriptionId, null) as Boolean
            } catch (_: NoSuchMethodException) {
                val isActiveSubIdMethod =
                    sub.javaClass.getMethod(
                        "isActiveSubId",
                        Int::class.javaPrimitiveType,
                    )
                isActiveSubIdMethod.invoke(sub, subscriptionId) as Boolean
            }
        }

    fun updateCarrierConfig(
        key: String,
        value: Boolean,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBoolean(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: String,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putString(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Int,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putInt(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Long,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLong(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: IntArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putIntArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: BooleanArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBooleanArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Array<String>,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putStringArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: LongArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLongArray(key, value) }
    }

    fun clearCarrierConfig() {
        this.overrideConfig(null)
    }

    fun restartIMSRegistration() {
        this.setAirplaneMode(false)
        val telephony = this.loadCachedInterface { telephony }
        try {
            telephony.resetIms(this.simSlotIndex)
        } catch (_: NoSuchMethodError) {
            telephony.disableIms(this.simSlotIndex)
            telephony.enableIms(this.simSlotIndex)
        }
    }

    fun getStringValue(key: String): String? {
        Log.d(TAG, "Resolving string value of key $key")
        if (this.subscriptionId < 0) {
            return ""
        }
        return this.config?.getString(key)
    }

    fun getBooleanValue(key: String): Boolean {
        Log.d(TAG, "Resolving boolean value of key $key")
        if (this.subscriptionId < 0) {
            return false
        }
        return this.config?.getBoolean(key) ?: false
    }

    fun getIntValue(key: String): Int {
        Log.d(TAG, "Resolving integer value of key $key")
        if (this.subscriptionId < 0) {
            return -1
        }
        return this.config?.getInt(key) ?: -1
    }

    fun getLongValue(key: String): Long {
        Log.d(TAG, "Resolving long value of key $key")
        if (this.subscriptionId < 0) {
            return -1
        }
        return this.config?.getLong(key) ?: -1L
    }

    fun getBooleanArrayValue(key: String): BooleanArray {
        Log.d(TAG, "Resolving boolean array value of key $key")
        if (this.subscriptionId < 0) {
            return booleanArrayOf()
        }
        return this.config?.getBooleanArray(key) ?: BooleanArray(0)
    }

    fun getIntArrayValue(key: String): IntArray {
        Log.d(TAG, "Resolving integer value of key $key")
        if (this.subscriptionId < 0) {
            return intArrayOf()
        }
        return this.config?.getIntArray(key) ?: IntArray(0)
    }

    fun getStringArrayValue(key: String): Array<String> {
        Log.d(TAG, "Resolving string array value of key $key")
        if (this.subscriptionId < 0) {
            return arrayOf()
        }
        return this.config?.getStringArray(key) ?: emptyArray()
    }

    fun getValue(key: String): Any? {
        Log.d(TAG, "Resolving value of key $key")
        if (this.subscriptionId < 0) {
            return null
        }
        return this.config?.get(key)
    }

    protected val config: PersistableBundle?
        get() {
            val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
            try {
                return iCclInstance.getConfigForSubIdWithFeature(this.subscriptionId, iCclInstance.defaultCarrierServicePackageName, null)
            } catch (_: NoSuchMethodError) {
            }
            return try {
                iCclInstance.getConfigForSubId(this.subscriptionId, iCclInstance.defaultCarrierServicePackageName)
            } catch (_: NoSuchMethodError) {
                val getConfigForSubIdMethod =
                    iCclInstance.javaClass.getMethod(
                        "getConfigForSubId",
                        Int::class.javaPrimitiveType,
                    )
                (getConfigForSubIdMethod.invoke(iCclInstance, this.subscriptionId) as? PersistableBundle)
            }
        }

    protected val mmTelConfig: IImsConfig?
        get() {
            return try {
                val telephony = this.loadCachedInterface { telephony }
                telephony
                    .getImsConfig(this.simSlotIndex, ImsFeature.FEATURE_MMTEL)
            } catch (_: RemoteException) {
                null
            }?.asBinder()?.cache(IImsConfig.Stub::asInterface)
        }

    fun isMmTelProvisioningRequired(
        capability: Int,
        tech: Int,
    ): Boolean {
        return try {
            val telephony = this.loadCachedInterface { telephony }
            telephony.isProvisioningRequiredForCapability(this.subscriptionId, capability, tech)
        } catch (_: NoSuchMethodError) {
            val featureKey = CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE
            val capabilityKey = KEYS_MMTEL_CAPABILITY.get(capability)
            val provisioningBundle = this.config?.getPersistableBundle(featureKey)
            val techArray = provisioningBundle?.getIntArray(capabilityKey)
            techArray
                ?.takeIf { it.contains(tech) }
                ?.let { return true }
            if (capability == MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE ||
                capability == MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO ||
                capability == MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT
            ) {
                val key =
                    if (capability == MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT) {
                        CarrierConfigManager.KEY_CARRIER_UT_PROVISIONING_REQUIRED_BOOL
                    } else {
                        CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
                    }
                this.getBooleanValue(key)
            } else {
                false
            }
        }
    }

    fun getImsProvisionedBoolNoException(
        capability: Int,
        tech: Int,
    ): Boolean {
        try {
            val telephony = this.loadCachedInterface { telephony }
            return telephony.getImsProvisioningStatusForCapability(this.subscriptionId, capability, tech)
        } catch (e: RemoteException) {
            Log.d(TAG, "getImsProvisionedBoolNoException", e)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "getImsProvisionedBoolNoException", e)
        }
        return false
    }

    fun setImsProvisionedBoolNoException(
        capability: Int,
        tech: Int,
        isProvisioned: Boolean,
    ) {
        try {
            val telephony = this.loadCachedInterface { telephony }
            telephony.setImsProvisioningStatusForCapability(this.subscriptionId, capability, tech, isProvisioned)
        } catch (e: RemoteException) {
            Log.d(TAG, "getImsProvisionedBoolNoException", e)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "getImsProvisionedBoolNoException", e)
        }
    }

    fun getProvisionedBoolNoException(key: Int): Boolean {
        var value =
            try {
                val telephony = this.loadCachedInterface { telephony }
                telephony.getImsProvisioningInt(this.subscriptionId, key)
            } catch (_: NoSuchMethodError) {
                ImsConfigImplBase.CONFIG_RESULT_UNKNOWN
            }
        if (value == ImsConfigImplBase.CONFIG_RESULT_UNKNOWN) {
            try {
                value = this.mmTelConfig?.getConfigInt(key) ?: ImsConfigImplBase.CONFIG_RESULT_UNKNOWN
            } catch (_: ImsException) {
                Log.d(TAG, "getImsProvisionedBoolNoException", e)
            }
        }
        return value == ProvisioningManager.PROVISIONING_VALUE_ENABLED
    }

    fun setProvisionedBoolNoException(
        key: Int,
        value: Int,
    ): Boolean {
        var result =
            try {
                val telephony = this.loadCachedInterface { telephony }
                telephony.setImsProvisioningInt(this.subscriptionId, key, value)
            } catch (_: NoSuchMethodError) {
                ImsConfigImplBase.CONFIG_RESULT_UNKNOWN
            }
        if (result == ImsConfigImplBase.CONFIG_RESULT_UNKNOWN) {
            try {
                result = this.mmTelConfig?.setConfigInt(key, value) ?: ImsConfigImplBase.CONFIG_RESULT_UNKNOWN
            } catch (e: ImsException) {
                Log.d(TAG, "getImsProvisionedBoolNoException", e)
            }
        }
        return result == ImsConfigImplBase.CONFIG_RESULT_SUCCESS
    }

    fun getSubscriptionProperty(key: String): String? {
        val sub = this.loadCachedInterface { sub }
        return try {
            sub.getSubscriptionProperty(this.subscriptionId, key, null, null)
        } catch (_: NoSuchMethodError) {
            val getSubscriptionPropertyMethod =
                sub.javaClass.getMethod(
                    "getSubscriptionProperty",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                )
            (getSubscriptionPropertyMethod.invoke(sub, this.subscriptionId, key, null) as? String)
        }
    }

    fun setSubscriptionProperty(
        key: String,
        value: String,
    ) {
        val sub = this.loadCachedInterface { sub }
        try {
            sub.setSubscriptionProperty(this.subscriptionId, key, value)
        } catch (_: NoSuchMethodError) {
            val setSubscriptionPropertyMethod =
                sub.javaClass.getMethod(
                    "setSubscriptionProperty",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                )
            setSubscriptionPropertyMethod.invoke(sub, this.subscriptionId, key, value)
        }
    }

    val simSlotIndex: Int
        get() = this.loadCachedInterface { sub }.getSlotIndex(subscriptionId)

    val isGbaValid: Boolean
        get() {
//            if (!this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL)) {
//                return true
//            }
            Log.d(TAG, "isGbaValid")
            val phoneSubInfo = this.loadCachedNullableInterface { phoneSubInfo } ?: return false
            val efIst = phoneSubInfo.getIsimIst(this.subscriptionId) ?: return true
            val result = efIst.length > 1 &&
                    (0x02 and efIst[1].code) != 0
            Log.d(TAG, "isGbaValid - GBA capable=" + result + ", ISF=" + efIst)
            return result
        }

    fun setGbaRequired(required: Boolean) {
        this.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL, required)
    }

    val isTtyOnVolteEnabled: Boolean
        get() =
            try {
                val telephony = this.loadCachedInterface { telephony }
                telephony.isTtyOverVolteEnabled(this.subscriptionId)
            } catch (_: NoSuchMethodError) {
                this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL)
            }

    val isNonTtyOrTtyOnVolteEnabled: Boolean
        get() {
            if (this.isTtyOnVolteEnabled) {
                return true
            }
            val telecom = this.loadCachedNullableInterface { telecom } ?: return true
            return try {
                telecom.getCurrentTtyMode(null, null)
            } catch (_: NoSuchMethodError) {
                val getCurrentTtyModeMethod = telecom.javaClass.getMethod(
                    "getCurrentTtyMode",
                    String::class.java,
                )
                (getCurrentTtyModeMethod.invoke(telecom, null) as Int)
            } == TelecomManager.TTY_MODE_OFF
        }

    val isVolteProvisioned: Boolean
        get() =
            !this.isMmTelProvisioningRequired(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
            ) || try {
                this.getImsProvisionedBoolNoException(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                )
            } catch (_: NoSuchMethodError) {
                this.getProvisionedBoolNoException(ImsConfig.ConfigConstants.VLT_SETTING_ENABLED)
            }

    fun setVoLteProvisioned(isProvisioned: Boolean) {
        try {
            this.setImsProvisionedBoolNoException(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                isProvisioned,
            )
        } catch (_: NoSuchMethodError) {
            val provisionStatus =
                if (isProvisioned) {
                    ProvisioningManager.PROVISIONING_VALUE_ENABLED
                } else {
                    ProvisioningManager.PROVISIONING_VALUE_DISABLED
                }
            this.setProvisionedBoolNoException(ImsConfig.ConfigConstants.VLT_SETTING_ENABLED, provisionStatus)
        }
    }

    val isVolteUiEditable: Boolean
        get() =
            this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL) &&
                    !this.getBooleanValue(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)

    val isVolteSupportedBySystem: Boolean
        get() {
            val sub = this.loadCachedInterface { sub }
            val phoneId = sub.getPhoneId(this.subscriptionId)
            return SystemProperties.getInt(PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE + phoneId.toString(), -1) == 1 ||
                SystemProperties.getInt(PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE, -1) == 1
        }

    val isVolteSupportedByUser: Boolean
        get() {
            val propValue = this.getSubscriptionProperty(SubscriptionManager.VOIMS_OPT_IN_STATUS)
            val setting = propValue?.toInt()
            return setting == ProvisioningManager.PROVISIONING_VALUE_ENABLED
        }

    fun setVolteSupportedByUser(enabled: Boolean) {
        val setting = if (enabled) ProvisioningManager.PROVISIONING_VALUE_ENABLED else ProvisioningManager.PROVISIONING_VALUE_DISABLED
        this.setProvisionedBoolNoException(ProvisioningManager.KEY_VOIMS_OPT_IN_STATUS, setting)
        try {
            val propValue = setting.toString()
            this.setSubscriptionProperty(SubscriptionManager.VOIMS_OPT_IN_STATUS, propValue)
        } catch (_: IllegalArgumentException) {
        } catch (_: InvocationTargetException) {
        }
        try {
            this.mmTelConfig?.notifyIntImsConfigChanged(ProvisioningManager.KEY_VOIMS_OPT_IN_STATUS, setting)
        } catch (_: NoSuchMethodError) {
        }
    }

    val isVolteSupportedByCarrier: Boolean
        get() =
            this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)

    val isVolteSupported: Boolean
        get() =
            this.isVolteSupportedBySystem || this.isVolteSupportedByUser || isVolteSupportedByDevice && this.isVolteSupportedByCarrier && this.isGbaValid

    val isLtePlusEnabledByCarrier: Boolean
        get() =
            this.getBooleanValue(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL)

    val isLtePlusEnabled: Boolean
        get() {
            val propValue = this.getSubscriptionProperty(SubscriptionManager.ENHANCED_4G_MODE_ENABLED)
            val setting = propValue?.toInt() ?: -1
            return if (this.isVolteSupportedByUser || this.isVolteUiEditable && setting != -1) {
                setting == ProvisioningManager.PROVISIONING_VALUE_ENABLED
            } else {
                this.isLtePlusEnabledByCarrier
            }
        }

    fun setVolteEnabled(enabled: Boolean) {
        if (!this.isVolteSupportedByUser && !this.isVolteUiEditable) {
            throw
        }
        this.updateCarrierConfig(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, enabled)
        this.updateCarrierConfig(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
        this.updateCarrierConfig(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
        this.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, true)
        try {
            val telephony = this.loadCachedInterface { telephony }
            telephony.setAdvancedCallingSettingEnabled(this.subscriptionId, !enabled)
            telephony.setAdvancedCallingSettingEnabled(this.subscriptionId, enabled)
        } catch (e: NoSuchMethodError) {
            Log.d(TAG, "set4GPlus", e)
        }
        try {
            val setting = if (enabled) ProvisioningManager.PROVISIONING_VALUE_ENABLED else ProvisioningManager.PROVISIONING_VALUE_DISABLED
            val propValue = setting.toString()
            this.setSubscriptionProperty(SubscriptionManager.ENHANCED_4G_MODE_ENABLED, propValue)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "set4GPlus", e)
        } catch (e: InvocationTargetException) {
            Log.d(TAG, "set4GPlus", e)
        }
    }

    val isVolteEnabled: Boolean
        get() =
            isVolteSupported && this.isLtePlusEnabled && this.isNonTtyOrTtyOnVolteEnabled && (Build.VERSION.SDK_INT <= VERSION_CODES.Q || this.isVolteProvisioned)

    val isVoNrConfigEnabled: Boolean
        @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            this.getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL) &&
                this.getBooleanValue(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL)

    val isCrossSIMConfigEnabled: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL) &&
                    this.getBooleanValue(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL)
            } else {
                false
            }
        }

    val isVoWifiConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)

    val isVoWifiWhileRoamingEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL)

    val showIMSinSIMInfo: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL)

    val allowAddingAPNs: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL)

    val showVoWifiMode: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL)

    val showVoWifiRoamingMode: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL)

    val wfcSpnFormatIndex: Int
        get() = this.getIntValue(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT)

    val carrierName: String?
        get() = this.loadCachedInterface { telephony }.getSubscriptionCarrierName(this.subscriptionId)

    val showVoWifiIcon: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL)

    val alwaysDataRATIcon: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL)

    val supportWfcWifiOnly: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL)

    val isVtConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL)

    val ssOverUtEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL)
            } else {
                false
            }

    val ssOverCDMAEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL)

    val isShow4GForLteEnabled: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL)

    val isHideEnhancedDataIconEnabled: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL)

    val isNRConfigEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                this
                    .getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
                    .contentEquals(intArrayOf(1, 2))
            } else {
                false
            }

    val userAgentConfig: String?
        get() = this.getStringValue(KEY_IMS_USER_AGENT)

    val isIMSRegistered: Boolean
        get() {
            val telephony = this.loadCachedInterface { telephony }
            return telephony.isImsRegistered(this.subscriptionId)
        }
}
