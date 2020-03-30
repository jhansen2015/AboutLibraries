package com.mikepenz.aboutlibraries.plugin


import com.mikepenz.aboutlibraries.plugin.mapping.Library
import com.mikepenz.aboutlibraries.plugin.mapping.License
import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets

@CacheableTask
class AboutLibrariesTask extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AboutLibrariesTask.class)

    private static final includeFields = []
    private static final cdataFields = []

    @Internal
    Set<String> neededLicenses = new HashSet<String>()

    private File dependencies
    private Configuration configuration

    @Internal
    private File combinedLibrariesOutputFile
    private File outputValuesFolder
    private File outputRawFolder

    File getCombinedLibrariesOutputFile() {
        return new File(outputValuesFolder, "aboutlibraries.xml")
    }

    @OutputDirectory
    File getValuesFolder() {
        return new File(dependencies, "values")
    }

    @OutputDirectory
    File getRawFolder() {
        return new File(dependencies, "raw")
    }

    @OutputDirectory
    File getDependencies() {
        return dependencies
    }

    @InputFiles
    void setDependencies(File dependencies) {
        this.dependencies = dependencies
    }

    @Input
    void setConfiguration(Configuration configuration) {
        this.configuration = configuration
    }

    def gatherDependencies(def project) {
        // ensure directories exist
        this.outputValuesFolder = getValuesFolder()
        this.outputRawFolder = getRawFolder()
        this.combinedLibrariesOutputFile = getCombinedLibrariesOutputFile()

        def libraries = new AboutLibrariesProcessor().gatherDependencies(project, configuration)
        def printWriter = new PrintWriter(new OutputStreamWriter(combinedLibrariesOutputFile.newOutputStream(), StandardCharsets.UTF_8), true)
        def combinedLibrariesBuilder = new MarkupBuilder(printWriter)
        combinedLibrariesBuilder.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        combinedLibrariesBuilder.doubleQuotes = true
        combinedLibrariesBuilder.resources {
            for (final library in libraries) {
                writeDependency(combinedLibrariesBuilder, library)

                if (isNotEmpty(library.licenseId)) {
                    neededLicenses.add(library.licenseId) // remember the license we hit
                }
            }
            string name: "config_aboutLibraries_plugin", "yes"
        }
        printWriter.close()

        processNeededLicenses()
    }

    /**
     * Looks inside the *.jar and tries to find a license file to include in the apk
     */
    def tryToFindAndWriteLibrary(def licenseId) {
        try {
            LOGGER.debug("--> Try load library with ID {}", licenseId)
            def successfulXml = false
            def resultFile = new File(outputValuesFolder, "license_${licenseId}_strings.xml")
            resultFile.delete()
            def is = getClass().getResourceAsStream("/values/license_${licenseId}_strings.xml")
            if (is != null) {
                resultFile.append(is)
                is.close()
                successfulXml = true
            } else {
                LOGGER.debug("--> File did not exist {}", getClass().getResource("values/license_${licenseId}_strings.xml"))
            }

            resultFile = new File(outputRawFolder, "license_${licenseId}.txt")
            resultFile.delete()
            is = getClass().getResourceAsStream("/static/license_${licenseId}.txt")
            if (is != null) {
                resultFile.append(is)
                is.close()
            }

            return successfulXml
        } catch (Exception ignored) {
            println("--> License not available: ${licenseId}")
        }
        return false
    }

    /**
     * Copy in the needed licenses to the relevant folder
     */
    def processNeededLicenses() {
        // now copy over all licenses
        for (String licenseId : neededLicenses) {
            try {
                def enumLicense = License.valueOf(licenseId)

                // try to find and write license by aboutLibsId
                if (!tryToFindAndWriteLibrary(enumLicense.aboutLibsId)) {
                    // try to find and write license by id
                    if (!tryToFindAndWriteLibrary(enumLicense.id)) {
                        // license was not available generate the url license template
                        def resultFile = new File(outputValuesFolder, "license_${licenseId.toLowerCase()}_strings.xml")
                        def printWriter = new PrintWriter(new OutputStreamWriter(resultFile.newOutputStream(), StandardCharsets.UTF_8), true)
                        def licenseBuilder = new MarkupBuilder(printWriter)
                        licenseBuilder.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
                        licenseBuilder.doubleQuotes = true
                        licenseBuilder.resources {
                            string name: "define_license_${licenseId}", translatable: 'false', ""
                            string name: "license_${licenseId}_licenseName", translatable: 'false', "${enumLicense.fullName}"
                            string name: "license_${licenseId}_licenseWebsite", translatable: 'false', "${enumLicense.getUrl()}"
                        }
                        printWriter.close()
                    }
                }
            } catch (Exception ignored) {
                try {
                    if (!tryToFindAndWriteLibrary(licenseId)) {
                        println("--> License not available: ${licenseId}")
                    }
                } catch (Exception ignored2) {
                    println("--> License not available: ${licenseId}")
                }
            }
        }
    }

    /**
     * Writes out the given library to disk
     */
    static def writeDependency(MarkupBuilder resources, Library library) {
        if( library.uniqueId == "com.mikepenz:aboutlibraries") {
            return
        }

        def delimiter = ""
        def customProperties = ""
        if (isNotEmpty(library.owner)) {
            customProperties = customProperties + delimiter + "owner"
            delimiter = ";"
        }
        if (isNotEmpty(library.year)) {
            customProperties = customProperties + delimiter + "year"
        }

        resources.string name: "define_plu_${library.uniqueId}", translatable: 'false', "${customProperties}"

        if( 0 == cdataFields.size() ) {
            cdataFields.addAll(detectFieldNames(library) {
                library.libraryDescription
            })
        }
        // Explicit order needed by AboutLibraries activity
        if( 0 == includeFields.size() ) {
            includeFields.addAll(detectFieldNames(library) {
                library.author
                library.authorWebsite
                library.libraryName
                library.libraryDescription
                library.libraryVersion
                library.libraryArtifactId
                library.libraryWebsite
                library.licenseId
                library.isOpenSource
                library.repositoryLink
                library.owner
                library.year
            })
        }

        includeFields.each {
            LOGGER.debug("library uniqueId [{}] property [{}]=[{}]", library.uniqueId, it, library[it])
            if ((it == "isOpenSource" && !library[it])) {
                // Skip excluded fields
                // Skip isOpenSource attribute if it is false
            }
            else if( isNotEmpty(library[it]) ) {
                def value = encodeForAndroidResourceValue(library[it])
                if( cdataFields.contains(it) ) {
                    resources.string(name: "library_${library.uniqueId}_${it}", translatable: 'false') { inner ->
                        mkp.yieldUnescaped("<![CDATA[${value}]]>")
                    }
                }
                else {
                    resources.string name: "library_${library.uniqueId}_${it}", translatable: 'false', "${value}"
                }
            }
        }
        
    }

    static class DetectingMetaClass extends DelegatingMetaClass {

        final def fields = []

        DetectingMetaClass(MetaClass metaClass) { super(metaClass) }

        @Override
        synchronized Object getProperty(Object object, String propertyName) {
            println("Detecting 3 property name ${propertyName}")
            fields.add(propertyName)
            return super.getProperty(object, propertyName)
        }
    }

    /**
     * <p>Detects field names by overriding the metaClass property method for object, then
     * executing propNameReferenceClosure, which should contain a sequence of object.prop1,
     * object.prop2, etc. references.</p>
     *
     * @param object Object that will be referenced in the closure. Don't use an object that
     * @param propNameReferenceClosure This MUST reference properties on object (the same instance)
     * @return List of properties referenced in the closure.
     */
    static def detectFieldNames(final object, final propNameReferenceClosure) {
        // Synchronized on the object to prevent bleeding property references from
        // other threads.
        synchronized (object) {
            final def originalMetaClass = object.metaClass
            final def detectingMeta = new DetectingMetaClass(object.metaClass)
            object.metaClass = detectingMeta

            propNameReferenceClosure()

            // Restore the original implementation
            object.metaClass = originalMetaClass
            return detectingMeta.fields
        }
    }

//    // Mechanism to detect the field names by referencing the properties,
//    // which allows explicity listing of property names while preserving
//    // the refactorability.
//    static def detectFieldNames(propNameReferenceClosure) {
//        def fields = []
//        Library.metaClass.getProperty = {
//            synchronized (fields) {
//                if( ! fields.contains(it) ) {
//                    fields.add(it)
//                }
//            }
//            Library.metaClass.getMetaProperty(it).getProperty(delegate)
//        }
//        propNameReferenceClosure()
//        // Restore the original implementation
//        println("Restoring original method...")
//        Library.metaClass.getProperty = {
//            Library.metaClass.getMetaProperty(it).getProperty(delegate)
//        }
//        return fields
//    }

    /**
     * Checks if the given string is empty.
     * Returns true if it is NOT empty
     */
    static def isNotEmpty(Object value) {
        return value != null && value.toString() != ""
    }

    /**
     * Ensures all characters necessary are escaped
     */
    static def encodeForAndroidResourceValue(Object value) {
        def result = ""
        if (value != null) {
            final def source = value.toString()
            result = source
                    .replace("\\", "")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")

            if( LOGGER.isDebugEnabled() && source != result ) {
                LOGGER.debug("Fixed string from \n[{}]\n to \n[{}]", source, result)
            }
        }

        return result
    }
    
    @TaskAction
    void action() throws IOException {
        gatherDependencies(project)
    }
}
