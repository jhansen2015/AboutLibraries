package com.mikepenz.aboutlibraries.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Paths

class AboutLibrariesExtension {
    String configPath
}

class AboutLibrariesPlugin implements Plugin<Project> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AboutLibrariesProcessor.class)

    @Override
    void apply(Project project) {
        // create the config possible
        project.extensions.create('aboutLibraries', AboutLibrariesExtension)

        final File outputFile = Paths.get("${project.buildDir}", "generated", "aboutlibraries").toFile()

        final def aboutLibrariesClean = project.tasks.register("aboutLibrariesClean") {
            description = "Cleans the generated data from the AboutLibraries plugin"
            outputs.upToDateWhen { !outputFile.exists() }
            doLast {
                outputFile.deleteDir()
            }
        }
        project.tasks.named("clean").configure() {
            dependsOn(aboutLibrariesClean)
        }

        final def exportLibraries = project.tasks.register("exportLibraries") {
            description = "Calls exportLibraries for each variant"
        }

        final def findLibraries = project.tasks.register("findLibraries") {
            description = "Calls findLibraries for each variant"
        }

        final def generateLibraryDefinitions = project.tasks.register("generateLibraryDefinitions") {
            description = "Calls generateLibraryDefinitions for each variant"
        }

        project.android.applicationVariants.all { final variant ->

            final Configuration config = variant.runtimeConfiguration
            LOGGER.debug "variant name: ${variant.name}, config name: ${config.name}"

            final AboutLibrariesTask generateLibrariesTask = project.tasks.create("generateLibraryDefinitions${variant.name.capitalize()}", AboutLibrariesTask) {
                description = "Writes the relevant metadata for the AboutLibraries plugin to display dependencies for variant ${variant.name}"
                dependencies = Paths.get("${outputFile}", variant.name, "res").toFile()
                configuration = config
            }
            generateLibraryDefinitions.configure() {
                dependsOn(generateLibrariesTask)
            }

            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.hasProperty("preBuildProvider")) {
                variant.preBuildProvider.configure { dependsOn(generateLibrariesTask) }
            }
            else {
                //noinspection GrDeprecatedAPIUsage
                variant.preBuild.dependsOn(generateLibrariesTask)
            }

            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.respondsTo("registerGeneratedResFolders")) {
                generateLibrariesTask.ext.generatedResFolders = project.files(generateLibrariesTask.getDependencies()).builtBy(generateLibrariesTask)
                variant.registerGeneratedResFolders(generateLibrariesTask.generatedResFolders)

                if (variant.hasProperty("mergeResourcesProvider")) {
                    variant.mergeResourcesProvider.configure { dependsOn(generateLibrariesTask) }
                }
                else {
                    //noinspection GrDeprecatedAPIUsage
                    variant.mergeResources.dependsOn(generateLibrariesTask)
                }
            }
            else {
                //noinspection GrDeprecatedAPIUsage
                variant.registerResGeneratingTask(generateLibrariesTask, generateLibrariesTask.getDependencies())
            }

            final def exportLibrariesTask = project.tasks.register("exportLibraries${variant.name.capitalize()}", AboutLibrariesExportTask) {
                description = "Writes all libraries for variant ${variant.name} and their licenses in CSV format to the CLI"
                configuration = config
            }
            exportLibraries.configure() {
                dependsOn(exportLibrariesTask)
            }

            final def findLibrariesTask = project.tasks.register("findLibraries${variant.name.capitalize()}", AboutLibrariesIdTask) {
                description = "Writes the relevant meta data for variant ${variant.name} for the AboutLibraries plugin to display dependencies"
                configuration = config
            }
            findLibraries.configure() {
                dependsOn(findLibrariesTask)
            }

        }

    }
}