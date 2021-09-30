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
package org.opensearch.observability.resthandler

import org.opensearch.observability.ObservabilityPlugin.Companion.BASE_NOTEBOOKS_URI
import org.opensearch.observability.action.NotebookActions
import org.opensearch.observability.model.RestTag.FROM_INDEX_FIELD
import org.opensearch.observability.model.RestTag.MAX_ITEMS_FIELD
import org.opensearch.observability.settings.PluginSettings
import org.opensearch.client.node.NodeClient
import org.opensearch.observability.index.ObservabilityQueryHelper
import org.opensearch.observability.model.RestTag.OBJECT_ID_LIST_FIELD
import org.opensearch.observability.model.RestTag.SORT_FIELD_FIELD
import org.opensearch.observability.model.RestTag.SORT_ORDER_FIELD
import org.opensearch.rest.BaseRestHandler
import org.opensearch.rest.BaseRestHandler.RestChannelConsumer
import org.opensearch.rest.BytesRestResponse
import org.opensearch.rest.RestHandler.Route
import org.opensearch.rest.RestRequest
import org.opensearch.rest.RestRequest.Method.GET
import org.opensearch.rest.RestStatus
import org.opensearch.search.sort.SortOrder

/**
 * Rest handler for getting list of notebooks.
 * This handler uses [NotebookActions].
 */
internal class NotebookListRestHandler : BaseRestHandler() {
    companion object {
        private const val NOTEBOOKS_LIST_ACTION = "notebooks_list_actions"
        private const val LIST_NOTEBOOKS_URL = "$BASE_NOTEBOOKS_URI/notebooks"
    }

    /**
     * {@inheritDoc}
     */
    override fun getName(): String {
        return NOTEBOOKS_LIST_ACTION
    }

    /**
     * {@inheritDoc}
     */
    override fun routes(): List<Route> {
        return listOf(
            /**
             * Get all notebooks (from optional fromIndex)
             * Request URL: GET LIST_NOTEBOOKS_URL[?[fromIndex=1000]&[maxItems=100]]
             * Request body: None
             * Response body: Ref [org.opensearch.observability.model.GetAllNotebooksResponse]
             */
            Route(GET, LIST_NOTEBOOKS_URL)
        )
    }

    /**
     * {@inheritDoc}
     */
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val objectIdList: String? = request.param(OBJECT_ID_LIST_FIELD)
        val sortField: String? = request.param(SORT_FIELD_FIELD)
        val sortOrderString: String? = request.param(SORT_ORDER_FIELD)
        val sortOrder: SortOrder? = if (sortOrderString == null) {
            null
        } else {
            SortOrder.fromString(sortOrderString)
        }
        val fromIndex = request.param(FROM_INDEX_FIELD)?.toIntOrNull() ?: 0
        val maxItems = request.param(MAX_ITEMS_FIELD)?.toIntOrNull() ?: PluginSettings.defaultItemsQueryCount
        val filterParams = request.params()
            .filter { ObservabilityQueryHelper.FILTER_PARAMS.contains(it.key) }
            .map { Pair(it.key, request.param(it.key)) }
            .toMap()
        return when (request.method()) {
            GET -> RestChannelConsumer {
//                client.execute(
//                    GetAllNotebooksAction.ACTION_TYPE,
//                    GetAllNotebooksRequest(
//                        getObjectIdSet(objectIdList),
//                        fromIndex,
//                        maxItems,
//                        sortField,
//                        sortOrder,
//                        filterParams
//                    ),
//                    RestResponseToXContentListener(it)
//                )
            }
            else -> RestChannelConsumer {
                it.sendResponse(BytesRestResponse(RestStatus.METHOD_NOT_ALLOWED, "${request.method()} is not allowed"))
            }
        }
    }

    private fun getObjectIdSet(objectIdList: String?): Set<String> {
        var retIds: Set<String> = setOf()
        if (objectIdList != null) {
            retIds = objectIdList.split(",").union(retIds)
        }
        return retIds
    }

    /**
     * {@inheritDoc}
     */
    override fun responseParams(): Set<String> {
        return setOf(FROM_INDEX_FIELD, MAX_ITEMS_FIELD)
    }
}
