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
import org.gradle.api.artifacts.ResolvedArtifact
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Based on https://raw.githubusercontent.com/gradle/gradle/master/subprojects/diagnostics/src/main/java/org/gradle/api/reporting/dependencies/internal/JsonProjectDependencyRenderer.java
 */
class DependencyCollector {
    private val logger: Logger = LoggerFactory.getLogger(DependencyCollector::class.java)

    /**
     * Generates the project dependency report structure
     *
     * @return set of artifact ModuleVersionIdentifier instances for the configuration sorted alphabetically by <code>group:name:version</code>
     */
    fun collect(configuration: Configuration): Set<ResolvedArtifact> {

        val moduleIds: MutableSet<ResolvedArtifact> = HashSet()

        configuration.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            logger.debug(
                    "Adding artifact for config name '{}' type '{}' moduleVersion.id '{}' (location '{}')",
                    configuration.name,
                    artifact.type,
                    artifact.moduleVersion.id,
                    artifact.file
            )
            moduleIds.add(artifact)
        }

        // Sort before handing back to Java/Groovy space
        return moduleIds.sortedBy { it.name }.toSet()
    }

}