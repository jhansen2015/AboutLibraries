/*
Copyright 2020 Joshua Hansen

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.upbear.groovy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DetectingMetaClass extends DelegatingMetaClass {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetectingMetaClass.class)

    final def fields = []

    DetectingMetaClass(MetaClass metaClass) { super(metaClass) }

    @Override
    synchronized Object getProperty(Object object, String propertyName) {
        LOGGER.debug("Detecting property name ${propertyName}")
        fields.add(propertyName)
        return super.getProperty(object, propertyName)
    }

    static interface Predicate<On> { void apply(On o) }

    /**
     * <p>Detects field names by creating an instance of <code>type</code>, overriding
     * the metaClass property method for the instance, then executing <code>predicate(instance)</code>
     * which should contain a sequence of instance.prop1, instance.prop2, etc. references.</p>
     * <p>Example</p>
     * <pre><code>
     * class MyClass {
     *     def prop1
     *     def prop2
     *     def prop3
     * }
     * 
     * println "Detected field names = " + DetectingMetaClass.detectFieldNamesFromType(MyClass) {
     *     // Predicate parameter type may be needed by some IDEs for type detection for refactoring
     *     MyClass it ->
     *       it.prop1
     *       it.prop2
     *       it.prop3
     *     }
     *
     * // Output: Detected field names = [prop1, prop2, prop3]
     * </code></pre>
     *
     * <p>References:<br />
     * <a href="http://docs.groovy-lang.org/latest/html/documentation/core-semantics.html#_parameters_inferred_from_single_abstract_method_types">Parameters inferred from single-abstract method types</a><br />
     *
     * @param type that will be used to create an object and passed to the closure. Don't use an object that
     * @param propNameReferenceClosure A closure which references properties on the type instance argument to it.
     * @return List of properties referenced in the closure.
     *
     */
    static <T> Object detectFieldNamesFromType(final Class<T> type, final Predicate<T> predicate) {
        final object = type.newInstance()
        final detectingMeta = new DetectingMetaClass(object.metaClass)
        object.metaClass = detectingMeta
        predicate.apply(object)
        return detectingMeta.fields
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
    static def detectFieldNamesFromInstance(final object, final Closure closure) {
        // Synchronized on the object to prevent bleeding property references from
        // other threads.
        synchronized (object) {
            final def originalMetaClass = object.metaClass
            final def detectingMeta = new DetectingMetaClass(object.metaClass)
            object.metaClass = detectingMeta

            closure()

            // Restore the original implementation
            object.metaClass = originalMetaClass
            return detectingMeta.fields
        }
    }
}