package com.mikepenz.aboutlibraries.plugin

import com.mikepenz.aboutlibraries.plugin.mapping.Library
import com.mikepenz.aboutlibraries.plugin.mapping.License
import groovy.xml.XmlUtil
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AboutLibrariesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AboutLibrariesProcessor.class)

    private File configFolder

    Set<String> handledLibraries = new HashSet<String>()

    Map<String, String> customLicenseMappings = new HashMap<String, String>()
    Map<String, String> customLicenseYearMappings = new HashMap<String, String>()
    Map<String, String> customNameMappings = new HashMap<String, String>()
    Map<String, String> customEnchantMapping = new HashMap<String, String>()

    def collectMappingDetails(targetMap, resourceName) {
        def customMappingText = getClass().getResource("/static/${resourceName}").getText('UTF-8')
        customMappingText.eachLine {
            def splitMapping = it.split(':')
            targetMap.put(splitMapping[0], splitMapping[1])
        }

        if (configFolder != null) {
            try {
                def target = new File(configFolder, "${resourceName}")
                if (target.exists()) {
                    customMappingText = target.getText('UTF-8')
                    customMappingText.eachLine {
                        def splitMapping = it.split(':')
                        targetMap.put(splitMapping[0], splitMapping[1])
                    }
                    println "Read custom mapping file from: ${target.absolutePath}"
                }
            } catch (Exception ex) {
                // ignored
            }
        }
    }

    def collectMappingDetails() {
        collectMappingDetails(customLicenseMappings, 'custom_license_mappings.prop')
        collectMappingDetails(customLicenseYearMappings, 'custom_license_year_mappings.prop')
        collectMappingDetails(customNameMappings, 'custom_name_mappings.prop')
        collectMappingDetails(customEnchantMapping, 'custom_enchant_mapping.prop')
    }

    def gatherDependencies(def project, configuration) {
        def extension = project.extensions.aboutLibraries
        if (extension.configPath != null) {
            configFolder = new File(extension.configPath)
        }

        // get all dependencies
        Set<ResolvedArtifact> collectedDependencies = new DependencyCollector().collect(configuration)

        println "All dependencies.size=${collectedDependencies.size()}"
        if (collectedDependencies.size() > 0) {
            collectMappingDetails()
        }

        def librariesList = new ArrayList<Library>()
        for (dependency in collectedDependencies) {
            LOGGER.debug("Processing artifact ${dependency}")
            def library = constructLibraryRecord(project, dependency)

            if( null != library ) {
                LOGGER.debug("Adding library record: {}", library)
                librariesList.add(library)
            }
            else {
                LOGGER.warn("Failed to construct library record for {}", dependency)
            }
        }
        return librariesList
    }

    def constructLibraryRecord(def project, def dependency) {

        File artifactFile = resolvePomFile(project, dependency.id.componentIdentifier, false)

        def artifactPomText = artifactFile.getText('UTF-8')
        def artifactPom = new XmlSlurper(/* validating */ false, /* namespaceAware */ false).parseText(artifactPomText)

        // the uniqueId
        def groupId = ifEmptyElse(artifactPom.groupId, artifactPom.parent.groupId)
        def uniqueId = fixIdentifier(groupId) + "__" + fixIdentifier(artifactPom.artifactId)

        LOGGER.debug(
                "--> ArtifactPom for [{}:{}]:\n{}\n\n",
                groupId,
                artifactPom.artifactId,
                artifactPomText
        )

        // check if we shall skip this specific uniqueId
        if (handledLibraries.contains(uniqueId)) {
            println "Skipping ${dependency.id.componentIdentifier}"
            return
        }

        // remember that we handled the library
        handledLibraries.add(uniqueId)

        // we also want to check if there are parent POMs with additional information
        def parentPomFile = resolvePomFile(project, getParentFromPom(artifactPom), true)
        def parentPom = null
        if (parentPomFile != null) {
            def parentPomText = parentPomFile.getText('UTF-8')
            LOGGER.debug(
                    "--> ArtifactPom ParentPom for [{}:{}]:\n{}\n\n",
                    groupId,
                    artifactPom.artifactId,
                    parentPomText
            )
            parentPom = new XmlSlurper(/* validating */ false, /* namespaceAware */ false).parseText(parentPomText)
        } else {
            LOGGER.debug(
                    "--> No Artifact Parent Pom found for [{}:{}]",
                    groupId,
                    artifactPom.artifactId,
            )
        }

        def enchantedDefinition = null
        if (customEnchantMapping.containsKey(uniqueId)) {
            def enchantedDefinitionId = customEnchantMapping.get(uniqueId)
            try {
                enchantedDefinition = new XmlSlurper(/* validating */ false, /* namespaceAware */ false)
                        .parseText(getClass().getResource("/values/library_${enchantedDefinitionId}_strings.xml").getText('UTF-8'))
            } catch (Exception ex) {
                println("--> Enchanted file not available: ${enchantedDefinitionId}")
            }
        }

        // get the author from the pom
        def author = fixXmlSlurperArray(artifactPom.developers.developer.name)
        if (isEmpty(author)) {
            // if no devs listed, use organisation
            author = fixXmlSlurperArray(artifactPom.organization.name)
        }
        if (isEmpty(author) && parentPom != null) { // fallback to parentPom if available
            author = fixXmlSlurperArray(parentPom.developers.developer.name)
            if (isEmpty(author)) {
                // if no devs listed, use organisation
                author = fixXmlSlurperArray(parentPom.organization.name)
            }
            if (!isEmpty(author)) {
                println("----> Had to fallback to parent author for: ${uniqueId} -- result: ${author}")
            }
        }
        if(!isEmpty(author)) {
            author = fixAuthor(author)
        }

        // get the url for the author
        def authorWebsite = fixXmlSlurperArray(artifactPom.developers.developer.organizationUrl)
        if (isEmpty(authorWebsite)) {
            // if no devs listed, use organisation
            authorWebsite = fixXmlSlurperArray(artifactPom.organization.url)
        }
        if (isEmpty(authorWebsite) && parentPom != null) { // fallback to parentPom if available
            authorWebsite = fixXmlSlurperArray(parentPom.developers.developer.organizationUrl)
            if (isEmpty(authorWebsite)) {
                // if no devs listed, use organisation
                authorWebsite = fixXmlSlurperArray(parentPom.organization.url)
            }
            if (!isEmpty(authorWebsite)) {
                println("----> Had to fallback to parent authorWebsite for: ${uniqueId} -- result: ${authorWebsite}")
            }
        }

        // get name of the library
        def libraryName = fixLibraryName(uniqueId, naes(artifactPom.name))

        // get the description of the library
        def libraryDescription = fixLibraryDescription(uniqueId, naes(artifactPom.description))
        if (enchantedDefinition != null) {
            // enchant the library by the description of the available definition file
            libraryDescription = ifEmptyElse(enchantedDefinition.string.find { it.@name.toString().endsWith("_libraryDescription") }.toString(), libraryDescription)
        }
        if (isEmpty(libraryDescription) && parentPom != null) {
            // fallback to parentPom if available
            println("----> Had to fallback to parent description for: ${uniqueId}")
            libraryDescription = fixLibraryDescription(uniqueId, naes(parentPom.description))
        }

        def libraryVersion = naes(artifactPom.version) // get the version of the library
        if (isEmpty(libraryVersion)) {
            // fallback to parent version if available
            libraryVersion = naes(artifactPom.parent.version)
            if (!isEmpty(libraryVersion)) {
                println("----> Had to fallback to parent version for: ${uniqueId} -- result: ${libraryVersion}")
            } else if (parentPom != null) {
                // fallback to parentPom if available
                libraryVersion = naes(parentPom.version)
                if (!isEmpty(libraryVersion)) {
                    println("----> Had to fallback to version in parent pom for: ${uniqueId} -- result: ${libraryVersion}")
                }
            }
        }
        if (isEmpty(libraryVersion)) {
            println("----> Failed to identify version for: ${uniqueId}")
        }

        def libraryWebsite = naes(artifactPom.url) // get the url to the library

        def licenseId = resolveLicenseId(uniqueId, naes(artifactPom.licenses.license.name), naes(artifactPom.licenses.license.url))
        if (isEmpty(licenseId) && parentPom != null) { // fallback to parentPom if available
            licenseId = resolveLicenseId(uniqueId, naes(parentPom.licenses.license.name), naes(parentPom.licenses.license.url))
            if (!isEmpty(licenseId)) {
                println("----> Had to fallback to parent licenseId for: ${uniqueId} -- result: ${licenseId}")
            }
        }
        // get the url to the library
        def repositoryLink = naes(artifactPom.scm.url)
        // TODO: This isn't a reliable way to determine open source...
        def isOpenSource = !isEmpty(repositoryLink)
        // assume if we have a link it is open source, may not always be accurate!
        def libraryOwner = naes(author)

        // the license year
        def licenseYear = resolveLicenseYear(uniqueId, repositoryLink)

        if (isEmpty(libraryName)) {
            println "Could not get the name for ${uniqueId}, Skipping"
            return
        }

// TODO: Convert to groovy, or, convert whole class to Kotlin.
//        val licensesZip = ZipFile(artifact.file)
////                JsonSlurper jsonSlurper = new JsonSlurper()
//
//        licensesZip.getEntry("third_party_licenses.json")?.let {
//            val content = licensesZip.getInputStream(it).bufferedReader().use(BufferedReader::readText)
//            println("third_party_licenses.json: [$content]")
//        }
//
//        licensesZip.getEntry("META-INF/MANIFEST.MF")?.let { txtFile ->
////                    val content = licensesZip.getInputStream(txtFile).bufferedReader().use(BufferedReader::readText)
//
//            Properties().apply {
//                licensesZip.getInputStream(txtFile).use { load(it) }
//
//                load(licensesZip.getInputStream(txtFile))
//                listOf(
//                        "Bundle-Description",
//                        "Bundle-License",
//                        "Bundle-License",
//                        "Bundle-Name",
//                        "Bundle-SymbolicName",
//                        "Bundle-Vendor",
//                        "Bundle-Version",
//                        "Implementation-Title",
//                        "Implementation-Version",
//                        "Implementation-Vendor"
//                ).forEach {
//                    if (containsKey(it)) {
//                        println("Found [" + it + "]=[" + this[it] + "]")
//                    }
//                }
//            }
//        }

        def library = new Library(
                uniqueId,
                dependency.id.componentIdentifier.displayName,
                author,
                authorWebsite,
                libraryName,
                libraryDescription,
                libraryVersion,
                libraryWebsite,
                licenseId,
                isOpenSource,
                repositoryLink,
                libraryOwner,
                licenseYear
        )
        return library
    }

    /**
     * returns value1 if it is not empty otherwise value2
     */
    static def ifEmptyElse(def value1, def value2) {
        if (value1 != null && !isEmpty(value1.toString())) {
            return value1
        } else {
            return value2
        }
    }

    /**
     * Ensures no invalid chars stay in the identifier
     */
    static def fixIdentifier(Object value) {
        return naes(value).replace(".", "_").replace("-", "_")
    }

    /**
     * Fix XmlSlurper array string
     */
    static def fixXmlSlurperArray(value) {
        if (value != null) {
            def delimiter = ""
            def resultString = ""
            for (item in value) {
                resultString = resultString + delimiter + item.toString()
                delimiter = ", "
            }
            return resultString
        } else {
            return null
        }
    }

    /**
     * Null-as-empty-String
     */
    static def naes(Object value) {
        return null == value ? "" : value.toString()
    }

    /**
     * Ensures the author name is not too long (for known options)
     */
    static def fixAuthor(String value) {
        if (value == "The Android Open Source Project") {
            return "AOSP"
        } else {
            return value
        }
    }

    /**
     * Ensures and applies fixes to the library names (shorten, ...)
     */
    def fixLibraryName(String uniqueId, String value) {
        if (customNameMappings.containsKey(uniqueId)) {
            def customMapping = customNameMappings.get(uniqueId)
            println("--> Had to resolve name from custom mapping for: ${uniqueId} as ${customMapping}")
            return customMapping
        } else if (value.startsWith("Android Support Library")) {
            return value.replace("Android Support Library", "Support")
        } else if (value.startsWith("Android Support")) {
            return value.replace("Android Support", "Support")
        } else if (value.startsWith("org.jetbrains.kotlin:")) {
            return value.replace("org.jetbrains.kotlin:", "")
        } else {
            return value
        }
    }

    /**
     * Ensures and applies fixes to the library descriptions (remove 'null', ...)
     */
    static def fixLibraryDescription(String uniqueId, String value) {
        if (value == "null") {
            return ""
        } else {
            return value
        }
    }

    /**
     * Ensures and applies fixes to the library names (shorten, ...)
     */
    def resolveLicenseId(String uniqueId, String name, String url) {
        if (customLicenseMappings.containsKey(uniqueId)) {
            def customMapping = customLicenseMappings.get(uniqueId)
            println("--> Had to resolve license from custom mapping for: ${uniqueId} as ${customMapping}")
            return customMapping
        } else {
            for (License l : License.values()) {
                def matcher = l.customMatcher
                if (l.id.equalsIgnoreCase(name) || l.name().equalsIgnoreCase(name) || l.fullName.equalsIgnoreCase(name) || (matcher != null && matcher.invoke(name, url))) {
                    return l.name()
                }
            }
        }
        return name
    }

    def resolveLicenseYear(String uniqueId, String repositoryLink) {
        if (customLicenseYearMappings.containsKey(uniqueId)) {
            def customMapping = customLicenseYearMappings.get(uniqueId)
            println("--> Had to resolve license year custom mapping for: ${uniqueId} as ${customMapping}")
            return customMapping
        } else {
            // TODO resolve via custom pom rule? try to resolve via git repo?
        }
        return ""
    }

    /**
     * Checks if the given string is empty.
     * Returns true if it is null or empty
     */
    static def isEmpty(String value) {
        return value == null || value == ""
    }

    /**
     * Looks in the pom if there is a parent we potentially could resolve
     *
     * Logic based on: https://github.com/ben-manes/gradle-versions-plugin
     */
    static ComponentIdentifier getParentFromPom(pom) {
        def parent = pom.children().find { child -> child.name() == 'parent' }
        if (parent) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Parent element: [{}]", XmlUtil.serialize(parent))
            }
            String groupId = parent.groupId
            String artifactId = parent.artifactId
            String version = parent.version
            if (groupId && artifactId && version) {
                return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(groupId, artifactId), version);
            }
        }
        return null
    }

    /**
     * Tries to resolve the pom file given the id if possible
     *
     * Logic based on: https://github.com/ben-manes/gradle-versions-plugin
     */
    File resolvePomFile(project, ComponentIdentifier componentIdentifier, parent) {
        try {
            if (null == componentIdentifier) {
                return null
            }
            LOGGER.debug("Attempting to resolve POM file for ComponentIdentifier id={}", componentIdentifier);
            ArtifactResolutionResult resolutionResult = project.dependencies.createArtifactResolutionQuery()
                    .forComponents(componentIdentifier)
                    .withArtifacts(MavenModule, MavenPomArtifact)
                    .execute()

            // size is 0 for gradle plugins, 1 for normal dependencies
            for (ComponentArtifactsResult result : resolutionResult.resolvedComponents) {
                LOGGER.debug("Processing component artifact result {}", result);
                // size should always be 1
                for (ArtifactResult artifact : result.getArtifacts(MavenPomArtifact)) {
                    LOGGER.debug("Processing artifact result {}", artifact);
                    // todo identify if that ever has more than 1
                    if (artifact instanceof ResolvedArtifactResult) {
                        if (parent) {
                            println "--> Retrieved POM for: ${componentIdentifier.displayName}"
                        }
                        return ((ResolvedArtifactResult) artifact).file
                    }
                }
            }
            return null
        } catch (Exception e) {
            e.printStackTrace()
            return null
        }
    }
}
