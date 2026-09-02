package com.sunmi.tapro.taplink.sdk.model.response

/**
 * Result of [com.sunmi.tapro.taplink.sdk.api.TaplinkApi.getTerminalInfo].
 *
 * Merchant and terminal information for the **single Tapro device that is currently
 * connected and being called**. This is not a cloud merchant lookup and not a terminal
 * list query — it never returns other devices, is not paginated, and has no
 * `terminals` array or `nextToken`.
 *
 * Field names at the top level intentionally mirror the public "Retrieve merchant" API
 * contract (`merchantId`, `dbaName`, `mcc`, `country`, `stateName`, `cityName`,
 * `street`, `detailAddress`, `zipCode`, `midList`), and [terminal] mirrors a single
 * element of the "List merchant terminals" API contract (`sn`, `vendor`, `model`,
 * `tidList`).
 *
 * Never contains `secretKey`, `appId`, network addresses, card data, or transaction
 * details.
 *
 * Note: merchant `status` and `createTime` (merchant and terminal level) are
 * intentionally **not** part of this response. Tapro has no real, backend-provisioned
 * source for them today, and this API must never fabricate values (e.g. `"Y"` or the
 * current time) to fill a field — so those fields are omitted entirely rather than
 * returned as `null`.
 *
 * @author Tapro Team
 * @since 2026-08-31
 */
data class TerminalInfo(
    /** Merchant ID. */
    val merchantId: String,

    /** Doing-business-as name. */
    val dbaName: String,

    /** Merchant category code. */
    val mcc: String? = null,

    /** Merchant country. */
    val country: String? = null,

    /** Merchant state/province name. */
    val stateName: String? = null,

    /** Merchant city name. */
    val cityName: String? = null,

    /** Merchant street. */
    val street: String? = null,

    /** Merchant detailed address. */
    val detailAddress: String? = null,

    /** Merchant zip/postal code. */
    val zipCode: String? = null,

    /** Merchant MIDs by payment channel. Empty list when none are configured. */
    val midList: List<MerchantMid> = emptyList(),

    /** The single Tapro terminal that answered this request. */
    val terminal: Terminal
)

/**
 * A merchant ID (MID) assigned for a specific payment channel.
 *
 * @author Tapro Team
 * @since 2026-08-31
 */
data class MerchantMid(
    /** Payment channel code (e.g. "TSYS"). */
    val channelCode: String,

    /** Payment channel display name. */
    val channelName: String,

    /** Merchant ID assigned by this channel. */
    val mid: String
)

/**
 * The single Tapro terminal that answered a [com.sunmi.tapro.taplink.sdk.api.TaplinkApi.getTerminalInfo]
 * request. This is device-specific information for the connected terminal only, not a
 * terminal-list entry.
 *
 * @author Tapro Team
 * @since 2026-08-31
 */
data class Terminal(
    /** Device serial number. */
    val sn: String,

    /** Real device vendor (e.g. "SUNMI"). Null when it cannot be determined. */
    val vendor: String? = null,

    /**
     * Device model (e.g. "P3", "P3K", "P2LiteSE", "P3Air", "CPadPay3", "P3MIX").
     * Never a connection-channel or generic placeholder value such as "CROSS_DEVICE"/"STANDALONE".
     */
    val model: String? = null,

    /** Terminal IDs (TIDs) by payment channel. Empty list when none are configured. */
    val tidList: List<TerminalTid> = emptyList()
)

/**
 * A terminal ID (TID) assigned for a specific payment channel.
 *
 * @author Tapro Team
 * @since 2026-08-31
 */
data class TerminalTid(
    /** Payment channel code (e.g. "TSYS"). */
    val channelCode: String,

    /** Payment channel display name. */
    val channelName: String,

    /** Terminal ID assigned by this channel. */
    val tid: String
)
