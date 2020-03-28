package com.mikepenz.aboutlibraries.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@CacheableTask
public class AboutLibrariesIdTask extends DefaultTask {

    private Configuration configuration

    @Input
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration
    }

    def gatherDependencies(def project) {
        def libraries = new AboutLibrariesProcessor().gatherDependencies(project, configuration)

        for (final library in libraries) {
           println "${library.libraryName} (${library.libraryVersion}) -> ${library.uniqueId}"
        }
    }

    @TaskAction
    public void action() throws IOException {
        gatherDependencies(project)
    }
}