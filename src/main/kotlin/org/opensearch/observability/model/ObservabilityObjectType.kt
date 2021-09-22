/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.observability.model

import org.opensearch.commons.utils.EnumParser

/**
 * Enum for Notification config type
 */
enum class ObservabilityObjectType(val tag: String) {
    NONE("none") {
        override fun toString(): String {
            return tag
        }
    },
    NOTEBOOK("notebook") {
        override fun toString(): String {
            return tag
        }
    },
    SAVED_QUERY("saved_query") {
        override fun toString(): String {
            return tag
        }
    },
    OPERATIONAL_PANEL("operational_panel") {
        override fun toString(): String {
            return tag
        }
    } ;

    companion object {
        private val tagMap = values().associateBy { it.tag }

        val enumParser = EnumParser { fromTagOrDefault(it) }

        /**
         * Get ConfigType from tag or NONE if not found
         * @param tag the tag
         * @return ConfigType corresponding to tag. NONE if invalid tag.
         */
        fun fromTagOrDefault(tag: String): ObservabilityObjectType {
            return tagMap[tag] ?: NONE
        }
    }
}

