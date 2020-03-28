package com.mikepenz.aboutlibraries.plugin

import com.mikepenz.aboutlibraries.plugin.mapping.License
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CacheableTask
public class AboutLibrariesExportTask extends DefaultTask {

    @Internal
    Set<License> neededLicenses = new HashSet<License>()
    Set<String> librariesWithoutLicenses = new HashSet<String>()
    HashMap<String, HashSet<String>> unknownLicenses = new HashMap<String, HashSet<String>>()

    private Configuration configuration

    @Input
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration
    }

    def gatherDependencies(def project) {
        def libraries = new AboutLibrariesProcessor().gatherDependencies(project, configuration)

        println ""
        println ""
        println "LIBRARIES:"

        for (final library in libraries) {
            try {
                neededLicenses.add(License.valueOf(library.licenseId))
            } catch (Exception ex) {
                if (library.licenseId != null && library.licenseId != "") {
                    HashSet<String> libsWithMissing = unknownLicenses.getOrDefault(library.licenseId, new HashSet<String>())
                    libsWithMissing.add(library.artifactId)
                    unknownLicenses.put(library.licenseId, libsWithMissing)
                } else {
                    librariesWithoutLicenses.add(library.artifactId)
                }
            }
            println "${library.libraryName};${library.artifactId};${library.licenseId}"
        }

        println ""
        println ""
        println "LICENSES:"

        for (final license in neededLicenses) {
            println "${license.id};${license.fullName};${license.url}"
        }

        println ""
        println ""
        println "ARTIFACTS WITHOUT LICENSE:"
        for (final license in librariesWithoutLicenses) {
            println "${license}"
        }

        println ""
        println ""
        println "UNKNOWN LICENSES:"
        for (final entry in unknownLicenses) {
            println "${entry.key}"
            println "-- ${entry.value}"
        }
    }

    @TaskAction
    public void action() throws IOException {
        gatherDependencies(project)
    }
}