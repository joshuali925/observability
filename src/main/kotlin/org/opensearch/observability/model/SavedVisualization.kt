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

import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.io.stream.Writeable
import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentFactory
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.observability.ObservabilityPlugin.Companion.LOG_PREFIX
import org.opensearch.observability.util.fieldIfNotNull
import org.opensearch.observability.util.logger
import org.opensearch.observability.util.stringList

/**
 * Saved query main data class.
 *  * <pre> JSON format
 * {@code
 * {
 *   "query": "source=index | where utc_time > timestamp('2021-07-01 00:00:00') and utc_time < timestamp('2021-07-02 00:00:00')",
 *   "selected_date_range": {
 *     "start": "now/15m",
 *     "end": "now",
 *     "text": "utc_time > timestamp('2021-07-01 00:00:00') and utc_time < timestamp('2021-07-02 00:00:00')"
 *   },
 *   "selected_fields": {
 *     "text": "| fields clientip, bytes, memory, host",
 *     "tokens": [
 *       "clientip",
 *       "bytes",
 *       "memory",
 *       "host"
 *     ]
 *   },
 *   "name": "Logs between dates",
 *   "description": "some descriptions related to this query"
 * }
 * }</pre>
 */

internal data class SavedVisualization(
    val name: String?,
    val description: String?,
    val query: String?,
    val selectedDateRange: SelectedDateRange?,
    val selectedFields: SelectedFields?
) : BaseObjectData {

    internal companion object {
        private val log by logger(SavedVisualization::class.java)
        private const val NAME_TAG = "name"
        private const val DESCRIPTION_TAG = "description"
        private const val QUERY_TAG = "query"
        private const val SELECTED_DATE_RANGE_TAG = "selected_date_range"
        private const val SELECTED_FIELDS_TAG = "selected_fields"

        /**
         * reader to create instance of class from writable.
         */
        val reader = Writeable.Reader { SavedVisualization(it) }

        /**
         * Parser to parse xContent
         */
        val xParser = XParser { parse(it) }

        /**
         * Parse the data from parser and create SavedVisualization object
         * @param parser data referenced at parser
         * @return created SavedVisualization object
         */
        fun parse(parser: XContentParser): SavedVisualization {
            var name: String? = null
            var description: String? = null
            var query: String? = null
            var selectedDateRange: SelectedDateRange? = null
            var selectedFields: SelectedFields? = null
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser)
            while (XContentParser.Token.END_OBJECT != parser.nextToken()) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    NAME_TAG -> name = parser.text()
                    DESCRIPTION_TAG -> description = parser.text()
                    QUERY_TAG -> query = parser.text()
                    SELECTED_DATE_RANGE_TAG -> selectedDateRange = SelectedDateRange.parse(parser)
                    SELECTED_FIELDS_TAG -> selectedFields = SelectedFields.parse(parser)
                    else -> {
                        parser.skipChildren()
                        log.info("$LOG_PREFIX:SavedVisualization Skipping Unknown field $fieldName")
                    }
                }
            }
            return SavedVisualization(name, description, query, selectedDateRange, selectedFields)
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

    /**
     * Constructor used in transport action communication.
     * @param input StreamInput stream to deserialize data from.
     */
    constructor(input: StreamInput) : this(
        name = input.readString(),
        description = input.readString(),
        query = input.readString(),
        selectedDateRange = input.readOptionalWriteable(SelectedDateRange.reader),
        selectedFields = input.readOptionalWriteable(SelectedFields.reader)
    )

    /**
     * {@inheritDoc}
     */
    override fun writeTo(output: StreamOutput) {
        output.writeString(name)
        output.writeString(description)
        output.writeString(query)
        output.writeOptionalWriteable(selectedDateRange)
        output.writeOptionalWriteable(selectedFields)
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        builder!!
        builder.startObject()
            .fieldIfNotNull(NAME_TAG, name)
            .fieldIfNotNull(DESCRIPTION_TAG, description)
            .fieldIfNotNull(QUERY_TAG, query)
            .fieldIfNotNull(SELECTED_DATE_RANGE_TAG, selectedDateRange)
            .fieldIfNotNull(SELECTED_FIELDS_TAG, selectedFields)
        return builder.endObject()
    }

    internal data class SelectedDateRange(
        val start: String,
        val end: String,
        val text: String
    ) : BaseModel {
        internal companion object {
            private const val START_TAG = "start"
            private const val END_TAG = "end"
            private const val TEXT_TAG = "text"

            /**
             * reader to create instance of class from writable.
             */
            val reader = Writeable.Reader { SelectedDateRange(it) }

            /**
             * Parser to parse xContent
             */
            val xParser = XParser { parse(it) }

            /**
             * Parse the data from parser and create Trigger object
             * @param parser data referenced at parser
             * @return created Trigger object
             */
            fun parse(parser: XContentParser): SelectedDateRange {
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
                return SelectedDateRange(start, end, text)
            }
        }

        /**
         * Constructor used in transport action communication.
         * @param input StreamInput stream to deserialize data from.
         */
        constructor(input: StreamInput) : this(
            start = input.readString(),
            end = input.readString(),
            text = input.readString()
        )

        /**
         * {@inheritDoc}
         */
        override fun writeTo(output: StreamOutput) {
            output.writeString(start)
            output.writeString(end)
            output.writeString(text)
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

    internal data class SelectedFields(
        val text: String?,
        val tokens: List<String>?
    ) : BaseModel {
        internal companion object {
            private const val TEXT_TAG = "text"
            private const val TOKENS_TAG = "tokens"

            /**
             * reader to create instance of class from writable.
             */
            val reader = Writeable.Reader { SelectedFields(it) }

            /**
             * Parser to parse xContent
             */
            val xParser = XParser { parse(it) }

            /**
             * Parse the data from parser and create Trigger object
             * @param parser data referenced at parser
             * @return created Trigger object
             */
            fun parse(parser: XContentParser): SelectedFields {
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
                return SelectedFields(text, tokens)
            }
        }

        /**
         * Constructor used in transport action communication.
         * @param input StreamInput stream to deserialize data from.
         */
        constructor(input: StreamInput) : this(
            text = input.readString(),
            tokens = input.readStringList()
        )

        /**
         * {@inheritDoc}
         */
        override fun writeTo(output: StreamOutput) {
            output.writeString(text)
            output.writeStringCollection(tokens)
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
