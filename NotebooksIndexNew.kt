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

package org.opensearch.observability.index

import org.opensearch.observability.ObservabilityPlugin.Companion.LOG_PREFIX
import org.opensearch.observability.model.notebook.NotebookDetails
import org.opensearch.observability.model.notebook.NotebookDetailsSearchResults
import org.opensearch.observability.model.RestTag.ACCESS_LIST_FIELD
import org.opensearch.observability.model.RestTag.TENANT_FIELD
import org.opensearch.observability.model.RestTag.UPDATED_TIME_FIELD
import org.opensearch.observability.settings.PluginSettings
import org.opensearch.observability.util.SecureIndexClient
import org.opensearch.observability.util.logger
import org.opensearch.action.DocWriteResponse
import org.opensearch.action.delete.DeleteRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.action.index.IndexRequest
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.update.UpdateRequest
import org.opensearch.client.Client
import org.opensearch.cluster.service.ClusterService
import org.opensearch.common.unit.TimeValue
import org.opensearch.common.xcontent.LoggingDeprecationHandler
import org.opensearch.common.xcontent.NamedXContentRegistry
import org.opensearch.common.xcontent.XContentType
import org.opensearch.index.query.QueryBuilders
import org.opensearch.observability.model.notebook.GetAllNotebooksRequest
import org.opensearch.search.builder.SearchSourceBuilder
import java.util.concurrent.TimeUnit

/**
 * Class for doing OpenSearch index operation to maintain notebooks in cluster.
 * This index is deprecated to .opensearch-observability, these operations are only for backwards compatibility.
 */
internal object NotebooksIndex {
    private val log by logger(NotebooksIndex::class.java)
    const val NOTEBOOKS_INDEX_NAME = ".opensearch-notebooks"
    private const val MAPPING_TYPE = "_doc"

    private lateinit var client: Client
    private lateinit var clusterService: ClusterService

    /**
     * Initialize the class
     * @param client The OpenSearch client
     * @param clusterService The OpenSearch cluster service
     */
    fun initialize(client: Client, clusterService: ClusterService) {
        this.client = SecureIndexClient(client)
        this.clusterService = clusterService
    }

    /**
     * Check if the index is created and available.
     * @return true if index is available, false otherwise
     */
    private fun isIndexExists(): Boolean {
        val clusterState = clusterService.state()
        return clusterState.routingTable.hasIndex(NOTEBOOKS_INDEX_NAME)
    }

    /**
     * create a new doc for notebookDetails
     * @param notebookDetails the Notebook details
     * @return notebook.id if successful, null otherwise
     * @throws java.util.concurrent.ExecutionException with a cause
     */
    fun createNotebook(notebookDetails: NotebookDetails): String? {
        if (!isIndexExists())
            throw IllegalStateException("$LOG_PREFIX:Index $NOTEBOOKS_INDEX_NAME does not exist, should use new index instead")
        val indexRequest = IndexRequest(NOTEBOOKS_INDEX_NAME)
            .source(notebookDetails.toXContent())
            .create(true)
        val actionFuture = client.index(indexRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        return if (response.result != DocWriteResponse.Result.CREATED) {
            log.warn("$LOG_PREFIX:createNotebook - response:$response")
            null
        } else {
            response.id
        }
    }

    /**
     * Query index for notebook ID
     * @param id the id for the document
     * @return Notebook details on success, null otherwise
     */
    fun getNotebook(id: String): NotebookDetails? {
        if (!isIndexExists())
            throw IllegalStateException("$LOG_PREFIX:Index $NOTEBOOKS_INDEX_NAME does not exist, should use new index instead")
        val getRequest = GetRequest(NOTEBOOKS_INDEX_NAME).id(id)
        val actionFuture = client.get(getRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        return if (response.sourceAsString == null) {
            log.warn("$LOG_PREFIX:getNotebook - $id not found; response:$response")
            null
        } else {
            val parser = XContentType.JSON.xContent().createParser(
                NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                response.sourceAsString
            )
            parser.nextToken()
            NotebookDetails.parse(parser, id)
        }
    }

    /**
     * Query index for notebook for given access details
     * @param tenant the tenant of the user
     * @param access the list of access details to search notebooks for.
     * @param from the paginated start index
     * @param maxItems the max items to query
     * @return search result of Notebook details
     */
    fun getAllNotebooks(tenant: String, access: List<String>, from: Int, maxItems: Int): NotebookDetailsSearchResults {
        if (!isIndexExists())
            throw IllegalStateException("$LOG_PREFIX:Index $NOTEBOOKS_INDEX_NAME does not exist, should use new index instead")
        val sourceBuilder = SearchSourceBuilder()
            .timeout(TimeValue(PluginSettings.operationTimeoutMs, TimeUnit.MILLISECONDS))
            .sort(UPDATED_TIME_FIELD)
            .size(maxItems)
            .from(from)
        val tenantQuery = QueryBuilders.termsQuery(TENANT_FIELD, tenant)
        if (access.isNotEmpty()) {
            val accessQuery = QueryBuilders.termsQuery(ACCESS_LIST_FIELD, access)
            val query = QueryBuilders.boolQuery()
            query.filter(tenantQuery)
            query.filter(accessQuery)
            sourceBuilder.query(query)
        } else {
            sourceBuilder.query(tenantQuery)
        }
        val searchRequest = SearchRequest()
            .indices(NOTEBOOKS_INDEX_NAME)
            .source(sourceBuilder)
        val actionFuture = client.search(searchRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        val result = NotebookDetailsSearchResults(from.toLong(), response)
        log.info(
            "$LOG_PREFIX:getAllNotebooks from:$from, maxItems:$maxItems," +
                " retCount:${result.objectList.size}, totalCount:${result.totalHits}"
        )
        return result
    }

    /**
     * update Notebook details for given id
     * @param id the id for the document
     * @param notebookDetails the Notebook details data
     * @return true if successful, false otherwise
     */
    fun updateNotebook(id: String, notebookDetails: NotebookDetails): Boolean {
        if (!isIndexExists())
            throw IllegalStateException("$LOG_PREFIX:Index $NOTEBOOKS_INDEX_NAME does not exist, should use new index instead")
        val updateRequest = UpdateRequest()
            .index(NOTEBOOKS_INDEX_NAME)
            .id(id)
            .doc(notebookDetails.toXContent())
            .fetchSource(true)
        val actionFuture = client.update(updateRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        if (response.result != DocWriteResponse.Result.UPDATED) {
            log.warn("$LOG_PREFIX:updateNotebook failed for $id; response:$response")
        }
        return response.result == DocWriteResponse.Result.UPDATED
    }

    /**
     * delete Notebook details for given id
     * @param id the id for the document
     * @return true if successful, false otherwise
     */
    fun deleteNotebook(id: String): Boolean {
        if (!isIndexExists())
            throw IllegalStateException("$LOG_PREFIX:Index $NOTEBOOKS_INDEX_NAME does not exist, should use new index instead")
        val deleteRequest = DeleteRequest()
            .index(NOTEBOOKS_INDEX_NAME)
            .id(id)
        val actionFuture = client.delete(deleteRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        if (response.result != DocWriteResponse.Result.DELETED) {
            log.warn("$LOG_PREFIX:deleteNotebook failed for $id; response:$response")
        }
        return response.result == DocWriteResponse.Result.DELETED
    }
}