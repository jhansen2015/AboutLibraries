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

        final def clean = project.tasks.named("clean")
        project.tasks.register("aboutLibrariesClean") {
            task ->
                description = "Cleans the generated data from the AboutLibraries plugin"
                clean.get().dependsOn(task)
                task.outputs.upToDateWhen {!outputFile.exists()}
                doLast {
                    outputFile.deleteDir()
                }
        }

        final def exportLibraries = project.tasks.register("exportLibraries") { task ->
            description = "Calls exportLibraries for each variant"
        }

        final def findLibraries = project.tasks.register("findLibraries") { task ->
            description = "Calls findLibraries for each variant"
        }

        project.android.applicationVariants.all { final variant ->

            final Configuration configuration = variant.runtimeConfiguration
            LOGGER.debug "variant name: ${variant.name}, config name: ${configuration.name}"

            final AboutLibrariesTask generateTask = project.tasks.create(
                    [name: "generateLibraryDefinitions${variant.name.capitalize()}", type: AboutLibrariesTask],
                    { task ->
                        description = "Writes the relevant metadata for the AboutLibraries plugin to display dependencies for variant ${variant.name}"
                        dependencies = Paths.get("${outputFile}", variant.name, "res").toFile()
                        task.configuration = configuration
                    }
            )

            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.hasProperty("preBuildProvider")) {
                variant.preBuildProvider.configure { dependsOn(generateTask) }
            }
            else {
                //noinspection GrDeprecatedAPIUsage
                variant.preBuild.dependsOn(generateTask)
            }

            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.respondsTo("registerGeneratedResFolders")) {
                generateTask.ext.generatedResFolders = project.files(generateTask.getDependencies()).builtBy(generateTask)
                variant.registerGeneratedResFolders(generateTask.generatedResFolders)

                if (variant.hasProperty("mergeResourcesProvider")) {
                    variant.mergeResourcesProvider.configure { dependsOn(generateTask) }
                } else {
                    //noinspection GrDeprecatedAPIUsage
                    variant.mergeResources.dependsOn(generateTask)
                }
            } else {
                //noinspection GrDeprecatedAPIUsage
                variant.registerResGeneratingTask(generateTask, generateTask.getDependencies())
            }

            final def exportLibrariesTask = project.tasks.register("exportLibraries${variant.name.capitalize()}", AboutLibrariesExportTask) {
                task ->
                    description = "Writes all libraries for variant ${variant.name} and their licenses in CSV format to the CLI"
                    task.configuration = configuration
            }
            exportLibraries.configure() {
                dependsOn(exportLibrariesTask)
            }

            final def findLibrariesTask = project.tasks.register("findLibraries${variant.name.capitalize()}", AboutLibrariesIdTask) {
                task ->
                    description = "Writes the relevant meta data for variant ${variant.name} for the AboutLibraries plugin to display dependencies"
                    task.configuration = configuration
            }
            findLibraries.configure() {
                dependsOn(findLibrariesTask)
            }

        }

    }
}