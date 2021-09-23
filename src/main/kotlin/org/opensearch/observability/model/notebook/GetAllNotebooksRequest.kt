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

/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package org.opensearch.observability.model.notebook

import org.opensearch.observability.ObservabilityPlugin.Companion.LOG_PREFIX
import org.opensearch.observability.model.RestTag.FROM_INDEX_FIELD
import org.opensearch.observability.model.RestTag.MAX_ITEMS_FIELD
import org.opensearch.observability.settings.PluginSettings
import org.opensearch.observability.util.logger
import org.opensearch.action.ActionRequest
import org.opensearch.action.ActionRequestValidationException
import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.ToXContentObject
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentFactory
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParser.Token
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.utils.STRING_READER
import org.opensearch.commons.utils.STRING_WRITER
import org.opensearch.commons.utils.enumReader
import org.opensearch.commons.utils.fieldIfNotNull
import org.opensearch.commons.utils.stringList
import org.opensearch.observability.model.RestTag.FILTER_PARAM_LIST_FIELD
import org.opensearch.observability.model.RestTag.OBJECT_ID_LIST_FIELD
import org.opensearch.observability.model.RestTag.SORT_FIELD_FIELD
import org.opensearch.observability.model.RestTag.SORT_ORDER_FIELD
import org.opensearch.search.sort.SortOrder
import java.io.IOException

/**
 * Get All notebooks info request
 * Data object created from GET request query params
 * <pre> JSON format
 * {@code
 * {
 *   "fromIndex":100,
 *   "maxItems":100
 * }
 * }</pre>
 */
internal class GetAllNotebooksRequest(
    val objectIds: Set<String>?,
    val fromIndex: Int,
    val maxItems: Int,
    val sortField: String?,
    val sortOrder: SortOrder?,
    val filterParams: Map<String, String>
) : ActionRequest(), ToXContentObject {

    companion object {
        private val log by logger(GetAllNotebooksRequest::class.java)

        /**
         * Parse the data from parser and create [GetAllNotebooksRequest] object
         * @param parser data referenced at parser
         * @return created [GetAllNotebooksRequest] object
         */
        fun parse(parser: XContentParser): GetAllNotebooksRequest {
            var objectIdList: Set<String> = setOf()
            var fromIndex = 0
            var maxItems = PluginSettings.defaultItemsQueryCount
            var sortField: String? = null
            var sortOrder: SortOrder? = null
            var filterParams: Map<String, String> = mapOf()

            XContentParserUtils.ensureExpectedToken(Token.START_OBJECT, parser.currentToken(), parser)
            while (Token.END_OBJECT != parser.nextToken()) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    OBJECT_ID_LIST_FIELD -> objectIdList = parser.stringList().toSet()
                    FROM_INDEX_FIELD -> fromIndex = parser.intValue()
                    MAX_ITEMS_FIELD -> maxItems = parser.intValue()
                    SORT_FIELD_FIELD -> sortField = parser.text()
                    SORT_ORDER_FIELD -> sortOrder = SortOrder.fromString(parser.text())
                    FILTER_PARAM_LIST_FIELD -> filterParams = parser.mapStrings()
                    else -> {
                        parser.skipChildren()
                        log.info("$LOG_PREFIX:Skipping Unknown field $fieldName")
                    }
                }
            }
            return GetAllNotebooksRequest(objectIdList, fromIndex, maxItems, sortField, sortOrder, filterParams)
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    constructor(input: StreamInput) : this(
        objectIds = input.readStringList().toSet(),
        fromIndex = input.readInt(),
        maxItems = input.readInt(),
        sortField = input.readOptionalString(),
        sortOrder = input.readOptionalWriteable(enumReader(SortOrder::class.java)),
        filterParams = input.readMap(STRING_READER, STRING_READER)
    )

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun writeTo(output: StreamOutput) {
        super.writeTo(output)
        output.writeStringCollection(objectIds)
        output.writeInt(fromIndex)
        output.writeInt(maxItems)
        output.writeOptionalString(sortField)
        output.writeOptionalWriteable(sortOrder)
        output.writeMap(filterParams, STRING_WRITER, STRING_WRITER)
    }

    /**
     * {@inheritDoc}
     */
    override fun validate(): ActionRequestValidationException? {
        return if (fromIndex < 0) {
            val exception = ActionRequestValidationException()
            exception.addValidationError("fromIndex should be grater than 0")
            exception
        } else if (maxItems <= 0) {
            val exception = ActionRequestValidationException()
            exception.addValidationError("maxItems should be grater than or equal to 0")
            exception
        } else {
            null
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
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        return builder!!.startObject()
            .field(OBJECT_ID_LIST_FIELD, objectIds)
            .field(FROM_INDEX_FIELD, fromIndex)
            .field(MAX_ITEMS_FIELD, maxItems)
            .fieldIfNotNull(SORT_FIELD_FIELD, sortField)
            .fieldIfNotNull(SORT_ORDER_FIELD, sortOrder)
            .field(FILTER_PARAM_LIST_FIELD, filterParams)
            .endObject()
    }
}
