package com.example.pelletswaterheater.utils

import kotlinx.serialization.Serializable

/**
 * Represents an open source software component with its license information.
 * Parsed from the build-time generated license report.
 */
@Serializable
data class SoftwareComponent(
    val name: String,
    val version: String,
    val license: String,
    val licenseUrl: String,
    val copyrightOwner: String? = null,
    val years: String? = null,
    val link: String? = null
)