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

        File outputFile = Paths.get("${project.buildDir}", "generated", "aboutlibraries").toFile()

        // task for cleaning
        def cleanupTask = project.tasks.create("aboutLibrariesClean", AboutLibrariesCleanTask)
        cleanupTask.description = "Cleans the generated data from the AboutLibraries plugin"
        cleanupTask.dependencies = outputFile
        project.tasks.findByName("clean").dependsOn(cleanupTask)

        project.android.applicationVariants.all { final variant ->

            final Configuration configuration = variant.runtimeConfiguration
            LOGGER.debug "variant name: ${variant.name}, config name: ${configuration.name}"

            AboutLibrariesTask task = project.tasks.create(
                [name: "generateLibraryDefinitions${variant.name.capitalize()}", type: AboutLibrariesTask],
                { task ->
                    description = "Writes the relevant metadata for the AboutLibraries plugin to display dependencies"
                    dependencies = Paths.get("${outputFile}", variant.name, "res").toFile()
                    task.configuration = configuration
                }
            )

            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.hasProperty("preBuildProvider")) {
                variant.preBuildProvider.configure { dependsOn(task) }
            }
            else {
                //noinspection GrDeprecatedAPIUsage
                variant.preBuild.dependsOn(task)
            }

            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.respondsTo("registerGeneratedResFolders")) {
                task.ext.generatedResFolders = project.files(task.getDependencies()).builtBy(task)
                variant.registerGeneratedResFolders(task.generatedResFolders)

                if (variant.hasProperty("mergeResourcesProvider")) {
                    variant.mergeResourcesProvider.configure { dependsOn(task) }
                } else {
                    //noinspection GrDeprecatedAPIUsage
                    variant.mergeResources.dependsOn(task)
                }
            } else {
                //noinspection GrDeprecatedAPIUsage
                variant.registerResGeneratingTask(task, task.getDependencies())
            }

        }

//        // TODO: Convert to per-variant, per-configuration.
//        // task to output library names with ids for further actions
//        AboutLibrariesIdTask taskId = project.tasks.create("findLibraries", AboutLibrariesIdTask)
//        taskId.description = "Writes the relevant meta data for the AboutLibraries plugin to display dependencies"
//
//        // TODO: Convert to per-variant, per-configuration
//        // task to output libraries, and their license in CSV format to the CLI
//        AboutLibrariesExportTask exportTaskId = project.tasks.create("exportLibraries", AboutLibrariesExportTask)
//        exportTaskId.description = "Writes all libraries and their license in CSV format to the CLI"
    }
}