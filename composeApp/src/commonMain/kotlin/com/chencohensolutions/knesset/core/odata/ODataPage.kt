package com.chencohensolutions.knesset.core.odata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ODataPage<T>(
    @SerialName("@odata.count") val count: Long? = null,
    val value: List<T> = emptyList(),
)

@Serializable
class ServiceDocument(val value: List<Entry> = emptyList()) {
    @Serializable
    class Entry(val name: String, val kind: String? = null, val url: String? = null)
}
