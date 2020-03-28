/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mikepenz.aboutlibraries.plugin

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Based on https://raw.githubusercontent.com/gradle/gradle/master/subprojects/diagnostics/src/main/java/org/gradle/api/reporting/dependencies/internal/JsonProjectDependencyRenderer.java
 */
class DependencyCollector {
    private val LOGGER: Logger = LoggerFactory.getLogger(DependencyCollector::class.java)

    /**
     * Generates the project dependency report structure
     *
     * @return set of artifact ModuleVersionIdentifier instances for the configuration sorted alphabetically by <code>group:name:version</code>
     */
    fun collect(configuration: Configuration): Set<ModuleVersionIdentifier> {
        // Sort before handing back to Java/Groovy space
        return createConfigurations(configuration).sortedBy { it.group + ":" + it.name + ":" + it.version }.toSet()
    }

    private fun createConfigurations(configuration: Configuration): Set<ModuleVersionIdentifier> {
        val moduleIds: MutableSet<ModuleVersionIdentifier> = HashSet()
        for (dependency in configuration.allDependencies) {
            configuration.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                val artifactId = artifact.moduleVersion.id
                moduleIds.add(artifactId)
                LOGGER.debug("Adding artifact for config name '{}' module '{}' (location '{}')", configuration.name, artifactId, artifact.file)
            }
        }
        return moduleIds
    }

}