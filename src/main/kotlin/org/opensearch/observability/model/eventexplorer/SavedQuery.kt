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

import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.ToXContentObject
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentFactory
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.notifications.model.XParser
import org.opensearch.observability.ObservabilityPlugin.Companion.LOG_PREFIX
import org.opensearch.observability.model.BaseObjectData
import org.opensearch.observability.model.RestTag
import org.opensearch.observability.util.fieldIfNotNull
import org.opensearch.observability.util.logger
import org.opensearch.observability.util.stringList
import org.opensearch.search.sort.SortOrder

/**
 * Saved query main data class.
 *  * <pre> JSON format
 * {@code
 * {
 *     "query": "search source=opensearch_dashboards_sample_data_logs | where utc_time > timestamp('2021-07-01 00:00:00') and utc_time < timestamp('2021-07-02 00:00:00')",
 *     "filters": {
 *         "time_filter": {
 *             "start": "now/15m",
 *             "end": "now",
 *             "text": "utc_time > timestamp('2021-07-01 00:00:00') and utc_time < timestamp('2021-07-02 00:00:00')"
 *         },
 *         "field_filter": {
 *             "text": "bytes > 1000",
 *             "tokens": ['bytes > 1000']
 *         }
 *         "sort_filter": {
 *             "time": "desc",
 *             "ip": "asc"
 *         }
 *     },
 *     "queried_fields": {
 *         "text": "| fields clientip, bytes, memory, host",
 *         "tokens": ['clientip', 'bytes', 'memory', 'host']
 *     },
 *     "name" : "Logs between dates",
 *     "description": "some descriptions related to this query"
 * }
 * }</pre>
 */

internal data class SavedQuery(
    val name: String?,
    val description: String?,
    val query: String?,
    val queriedFields: FieldFilter?,
    val filters: Filters?
) : BaseObjectData {

    internal companion object {
        private val log by logger(SavedQuery::class.java)
        private const val NAME_TAG = "name"
        private const val DESCRIPTION_TAG = "description"
        private const val QUERY_TAG = "query"
        private const val QUERIED_FIELDS_TAG = "queriedFields"
        private const val FILTERS_TAG = "filters"

        /**
         * Parser to parse xContent
         */
        val xParser = XParser { parse(it) }

        /**
         * Parse the data from parser and create Notebook object
         * @param parser data referenced at parser
         * @return created Notebook object
         */
        fun parse(parser: XContentParser): SavedQuery {
            var name: String? = null
            var description: String? = null
            var query: String? = null
            var queriedFields: FieldFilter? = null
            var filters: Filters? = null
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser)
            while (XContentParser.Token.END_OBJECT != parser.nextToken()) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    NAME_TAG -> name = parser.text()
                    DESCRIPTION_TAG -> description = parser.text()
                    QUERY_TAG -> query = parser.text()
                    QUERIED_FIELDS_TAG -> queriedFields = FieldFilter.parse(parser)
                    FILTERS_TAG -> filters = Filters.parse(parser)
                    else -> {
                        parser.skipChildren()
                        log.info("$LOG_PREFIX:Notebook Skipping Unknown field $fieldName")
                    }
                }
            }
            return SavedQuery(name, description, query, queriedFields, filters)
        }
    }

    /**
     * create XContentBuilder from this object using [XContentFactory.jsonBuilder()]
     * @param params XContent parameters
     * @return created XContentBuilder object
     */
    fun toXContent(params: ToXContent.Params = ToXContent.EMPTY_PARAMS): XContentBuilder? {
        return toXContent(XContentFactory.jsonBuilder(), params)
    }

    override fun writeTo(out: StreamOutput?) {
        TODO("Not yet implemented")
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        val xContentParams = params ?: RestTag.REST_OUTPUT_PARAMS
        builder!!
        builder.startObject()
            .fieldIfNotNull(NAME_TAG, name)
            .fieldIfNotNull(DESCRIPTION_TAG, description)
            .fieldIfNotNull(QUERY_TAG, query)
            .fieldIfNotNull(QUERIED_FIELDS_TAG, queriedFields)
            .fieldIfNotNull(FILTERS_TAG, filters)
        return builder.endObject()
    }

    internal data class Filters(
        val timeFilter: TimeFilter?,
        val fieldFilter: FieldFilter?,
        val sortFilters: List<SortFilter>?
    ) : ToXContentObject {
        internal companion object {
            private const val TIME_FILTER_TAG = "timeFilter"
            private const val FIELD_FILTER_TAG = "fieldFilter"
            private const val SORT_FILTERS_TAG = "sortFilters"

            /**
             * Parse the item list from parser
             * @param parser data referenced at parser
             * @return created list of items
             */
            private fun parseItemList(parser: XContentParser): List<SortFilter> {
                val retList: MutableList<SortFilter> = mutableListOf()
                XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser)
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    retList.add(SortFilter.parse(parser))
                }
                return retList
            }

            /**
             * Parse the data from parser and create Format object
             * @param parser data referenced at parser
             * @return created Format object
             */
            fun parse(parser: XContentParser): Filters {
                var timeFilter: TimeFilter? = null
                var fieldFilter: FieldFilter? = null
                var sortFilters: List<SortFilter>? = null
                XContentParserUtils.ensureExpectedToken(
                    XContentParser.Token.START_OBJECT,
                    parser.currentToken(),
                    parser
                )
                while (XContentParser.Token.END_OBJECT != parser.nextToken()) {
                    val fieldName = parser.currentName()
                    parser.nextToken()
                    when (fieldName) {
                        TIME_FILTER_TAG -> timeFilter = TimeFilter.parse(parser)
                        FIELD_FILTER_TAG -> fieldFilter = FieldFilter.parse(parser)
                        SORT_FILTERS_TAG -> sortFilters = parseItemList(parser)
                        else -> {
                            parser.skipChildren()
                            log.info("$LOG_PREFIX:Format Skipping Unknown field $fieldName")
                        }
                    }
                }
                return Filters(timeFilter, fieldFilter, sortFilters)
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
            val xContentParams = params ?: RestTag.REST_OUTPUT_PARAMS
            builder!!
            builder.startObject()
                .fieldIfNotNull(TIME_FILTER_TAG, timeFilter)
                .fieldIfNotNull(FIELD_FILTER_TAG, fieldFilter)
            if (sortFilters != null) {
                builder.startArray(SORT_FILTERS_TAG)
                sortFilters.forEach { it.toXContent(builder, xContentParams) }
                builder.endArray()
            }
            builder.endObject()
            return builder
        }
    }

    internal data class TimeFilter(
        val start: String,
        val end: String,
        val text: String
    ) : ToXContentObject {
        internal companion object {
            private const val START_TAG = "start"
            private const val END_TAG = "end"
            private const val TEXT_TAG = "text"

            /**
             * Parse the data from parser and create Trigger object
             * @param parser data referenced at parser
             * @return created Trigger object
             */
            fun parse(parser: XContentParser): TimeFilter {
                var start: String? = null
                var end: String? = null
                var text: String? = null
                XContentParserUtils.ensureExpectedToken(
                    XContentParser.Token.START_OBJECT,
                    parser.currentToken(),
                    parser
                )
                while (XContentParser.Token.END_OBJECT != parser.nextToken()) {
                    val fieldName = parser.currentName()
                    parser.nextToken()
                    when (fieldName) {
                        START_TAG -> start = parser.text()
                        END_TAG -> end = parser.text()
                        TEXT_TAG -> text = parser.text()
                        else -> log.info("$LOG_PREFIX: Trigger Skipping Unknown field $fieldName")
                    }
                }
                start ?: throw IllegalArgumentException("$START_TAG field absent")
                end ?: throw IllegalArgumentException("$END_TAG field absent")
                text ?: throw IllegalArgumentException("$TEXT_TAG field absent")
                return TimeFilter(start, end, text)
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
            builder!!
            builder.startObject()
                .field(START_TAG, start)
                .field(END_TAG, end)
                .field(TEXT_TAG, text)
            builder.endObject()
            return builder
        }
    }

    internal data class FieldFilter(
        val text: String?,
        val tokens: List<String>?
    ) : ToXContentObject {
        internal companion object {
            private const val TEXT_TAG = "text"
            private const val TOKENS_TAG = "tokens"

            /**
             * Parse the data from parser and create Trigger object
             * @param parser data referenced at parser
             * @return created Trigger object
             */
            fun parse(parser: XContentParser): FieldFilter {
                var text: String? = null
                var tokens: List<String>? = null
                XContentParserUtils.ensureExpectedToken(
                    XContentParser.Token.START_OBJECT,
                    parser.currentToken(),
                    parser
                )
                while (XContentParser.Token.END_OBJECT != parser.nextToken()) {
                    val fieldName = parser.currentName()
                    parser.nextToken()
                    when (fieldName) {
                        TEXT_TAG -> text = parser.text()
                        TOKENS_TAG -> tokens = parser.stringList()
                        else -> log.info("$LOG_PREFIX: Trigger Skipping Unknown field $fieldName")
                    }
                }
                text ?: throw IllegalArgumentException("$TEXT_TAG field absent")
                tokens ?: throw IllegalArgumentException("$TOKENS_TAG field absent")
                return FieldFilter(text, tokens)
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
            builder!!
            builder.startObject()
                .field(TEXT_TAG, text)
                .field(TOKENS_TAG, tokens)
            builder.endObject()
            return builder
        }
    }
}
