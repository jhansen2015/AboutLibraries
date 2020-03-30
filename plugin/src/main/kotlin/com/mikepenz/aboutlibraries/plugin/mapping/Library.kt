package com.mikepenz.aboutlibraries.plugin.mapping

/**
 * Library class describing a library and its information
 */
data class Library(
        val uniqueId: String,
        val libraryArtifactId: String,
        val author: String?,
        val authorWebsite: String?,
        val libraryName: String?,
        val libraryDescription: String?,
        val libraryVersion: String?,
        val libraryWebsite: String?,
        val licenseId: String?,
        val isOpenSource: Boolean,
        val repositoryLink: String?,
        /** libraryOwner */
        val owner: String?,
        /** licenseYear */
        val year: String?
)