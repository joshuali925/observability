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

package org.opensearch.observability.model.eventexplorer

import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.ToXContentObject
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.observability.ObservabilityPlugin
import org.opensearch.observability.util.logger
import org.opensearch.search.sort.SortOrder


internal data class SortFilter(
    val sortField: String,
    val sortOrder: SortOrder
) : ToXContentObject {
    internal companion object {
        private val log by logger(SortFilter::class.java)
        private const val SORT_FIELD_TAG = "sortField"
        private const val SORT_ORDER_TAG = "sortOrder"

        /**
         * Parse the data from parser and create Trigger object
         * @param parser data referenced at parser
         * @return created Trigger object
         */
        fun parse(parser: XContentParser): SortFilter {
            var sortField: String? = null
            var sortOrder: SortOrder? = null
            XContentParserUtils.ensureExpectedToken(
                XContentParser.Token.START_OBJECT,
                parser.currentToken(),
                parser
            )
            while (XContentParser.Token.END_OBJECT != parser.nextToken()) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    SORT_FIELD_TAG -> sortField = parser.text()
                    SORT_ORDER_TAG -> sortOrder = SortOrder.fromString(parser.text())
                    else -> log.info("${ObservabilityPlugin.LOG_PREFIX}: Trigger Skipping Unknown field $fieldName")
                }
            }
            sortField ?: throw IllegalArgumentException("$SORT_FIELD_TAG field absent")
            sortOrder ?: throw IllegalArgumentException("$SORT_ORDER_TAG field absent")
            return SortFilter(sortField, sortOrder)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        builder!!
        builder.startObject()
            .field(SORT_FIELD_TAG, sortField)
            .field(SORT_ORDER_TAG, sortOrder)
        builder.endObject()
        return builder
    }
}
