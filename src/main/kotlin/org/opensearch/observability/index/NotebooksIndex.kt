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

import org.opensearch.ResourceAlreadyExistsException
import org.opensearch.action.DocWriteResponse
import org.opensearch.action.admin.indices.create.CreateIndexRequest
import org.opensearch.action.bulk.BulkRequest
import org.opensearch.action.delete.DeleteRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.action.get.GetResponse
import org.opensearch.action.get.MultiGetRequest
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
import org.opensearch.observability.ObservabilityPlugin.Companion.LOG_PREFIX
import org.opensearch.observability.model.ObservabilityObjectDoc
import org.opensearch.observability.model.ObservabilityObjectDocInfo
import org.opensearch.observability.model.ObservabilityObjectType
import org.opensearch.observability.model.RestTag.ACCESS_LIST_FIELD
import org.opensearch.observability.model.RestTag.TENANT_FIELD
import org.opensearch.observability.model.notebook.GetAllNotebooksRequest
import org.opensearch.observability.model.notebook.NotebookDetails
import org.opensearch.observability.model.notebook.NotebookDetailsSearchResults
import org.opensearch.observability.settings.PluginSettings
import org.opensearch.observability.util.SecureIndexClient
import org.opensearch.observability.util.logger
import org.opensearch.rest.RestStatus
import org.opensearch.search.builder.SearchSourceBuilder
import org.opensearch.search.sort.SortOrder
import java.util.concurrent.TimeUnit

/**
 * Class for doing OpenSearch index operation to maintain notebooks in cluster.
 */
internal object NotebooksIndex {
    private val log by logger(NotebooksIndex::class.java)
    const val INDEX_NAME = ".opensearch-notebooks"
    private const val OBSERVABILITY_MAPPING_FILE_NAME = "observability-mapping.yml"
    private const val OBSERVABILITY_SETTINGS_FILE_NAME = "observability-settings.yml"
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
     * Create index using the mapping and settings defined in resource
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createIndex() {
        if (!isIndexExists()) {
            val classLoader = NotebooksIndex::class.java.classLoader
            val indexMappingSource = classLoader.getResource(OBSERVABILITY_MAPPING_FILE_NAME)?.readText()!!
            val indexSettingsSource = classLoader.getResource(OBSERVABILITY_SETTINGS_FILE_NAME)?.readText()!!
            val request = CreateIndexRequest(INDEX_NAME)
                .mapping(MAPPING_TYPE, indexMappingSource, XContentType.YAML)
                .settings(indexSettingsSource, XContentType.YAML)
            try {
                val actionFuture = client.admin().indices().create(request)
                val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
                if (response.isAcknowledged) {
                    log.info("$LOG_PREFIX:Index $INDEX_NAME creation Acknowledged")
                } else {
                    throw IllegalStateException("$LOG_PREFIX:Index $INDEX_NAME creation not Acknowledged")
                }
            } catch (exception: Exception) {
                if (exception !is ResourceAlreadyExistsException && exception.cause !is ResourceAlreadyExistsException) {
                    throw exception
                }
            }
        } else {
            // else if index mapping is old variable is true
            // TODO update mapping here
        }
    }

    /**
     * Check if the index is created and available.
     * @return true if index is available, false otherwise
     */
    private fun isIndexExists(): Boolean {
        val clusterState = clusterService.state()
        return clusterState.routingTable.hasIndex(INDEX_NAME)
    }

    /**
     * create a new doc for notebookDetails
     * @param notebookDetails the Notebook details
     * @return notebook.id if successful, null otherwise
     * @throws java.util.concurrent.ExecutionException with a cause
     */
    fun createNotebook(notebookDetails: NotebookDetails): String? {
        createIndex()
        val indexRequest = IndexRequest(INDEX_NAME)
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
        createIndex()
        val getRequest = GetRequest(INDEX_NAME).id(id)
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
    fun getAllNotebooks(
        tenant: String,
        access: List<String>,
        request: GetAllNotebooksRequest
    ): NotebookDetailsSearchResults {
        createIndex()
        val queryHelper = ObservabilityQueryHelper(ObservabilityObjectType.NOTEBOOK)
        val sourceBuilder = SearchSourceBuilder()
            .timeout(TimeValue(PluginSettings.operationTimeoutMs, TimeUnit.MILLISECONDS))
            .sort(queryHelper.getSortField(request.sortField), request.sortOrder ?: SortOrder.ASC)
            .size(request.maxItems)
            .from(request.fromIndex)
        val query = QueryBuilders.boolQuery()
        query.filter(QueryBuilders.termsQuery(TENANT_FIELD, tenant))
//        query.filter(QueryBuilders.termsQuery(TYPE_FIELD, ObservabilityObjectType.NOTEBOOK))
        if (access.isNotEmpty()) {
            query.filter(QueryBuilders.termsQuery(ACCESS_LIST_FIELD, access))
        }
        queryHelper.addQueryFilters(query, request.filterParams)
        println("debughere")
        println("query:")
        println(query)
        sourceBuilder.query(query)
        val searchRequest = SearchRequest()
            .indices(INDEX_NAME)
            .source(sourceBuilder)
        val actionFuture = client.search(searchRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        val result = NotebookDetailsSearchResults(request.fromIndex.toLong(), response)
        log.info(
            "$LOG_PREFIX:getAllNotebooks from:${request.fromIndex}, maxItems:${request.maxItems}, " +
                "sortField:${request.sortField}, sortOrder:${request.sortOrder}, " +
                "filterParams:${request.filterParams}, objectIdList:${request.objectIds}, " +
                "retCount:${result.objectList.size}, totalCount:${result.totalHits}"
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
        createIndex()
        val updateRequest = UpdateRequest()
            .index(INDEX_NAME)
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
        createIndex()
        val deleteRequest = DeleteRequest()
            .index(INDEX_NAME)
            .id(id)
        val actionFuture = client.delete(deleteRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        if (response.result != DocWriteResponse.Result.DELETED) {
            log.warn("$LOG_PREFIX:deleteNotebook failed for $id; response:$response")
        }
        return response.result == DocWriteResponse.Result.DELETED
    }

    // ===================================== new methods

    fun createObservabilityObject(observabilityObjectDoc: ObservabilityObjectDoc): String? {
        createIndex()
        val xContent = observabilityObjectDoc.toXContent()
        println("debug index create")
        println(xContent)
        val indexRequest = IndexRequest(INDEX_NAME)
            .source(xContent)
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

    fun getObservabilityObject(id: String): ObservabilityObjectDocInfo? {
        createIndex()
        val getRequest = GetRequest(INDEX_NAME).id(id)
        val actionFuture = client.get(getRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        return parseObservabilityObjectDoc(id, response)
    }

    fun getObservabilityObjects(ids: Set<String>): List<ObservabilityObjectDocInfo> {
        createIndex()
        val getRequest = MultiGetRequest()
        ids.forEach { getRequest.add(INDEX_NAME, it) }
        val actionFuture = client.multiGet(getRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        return response.responses.mapNotNull { parseObservabilityObjectDoc(it.id, it.response) }
    }

    private fun parseObservabilityObjectDoc(id: String, response: GetResponse): ObservabilityObjectDocInfo? {
        return if (response.sourceAsString == null) {
            log.warn("$LOG_PREFIX:getObservabilityObject - $id not found; response:$response")
            null
        } else {
            val parser = XContentType.JSON.xContent().createParser(
                NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                response.sourceAsString
            )
            parser.nextToken()
            val doc = ObservabilityObjectDoc.parse(parser)
            ObservabilityObjectDocInfo(id, response.version, response.seqNo, response.primaryTerm, doc)
        }
    }

    fun updateObservabilityObject(id: String, observabilityObjectDoc: ObservabilityObjectDoc): Boolean {
        createIndex()
        val updateRequest = UpdateRequest()
            .index(INDEX_NAME)
            .id(id)
            .doc(observabilityObjectDoc.toXContent())
            .fetchSource(true)
        val actionFuture = client.update(updateRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        if (response.result != DocWriteResponse.Result.UPDATED) {
            log.warn("$LOG_PREFIX:updateObservabilityObject failed for $id; response:$response")
        }
        return response.result == DocWriteResponse.Result.UPDATED
    }

    fun deleteObservabilityObject(id: String): Boolean {
        createIndex()
        val deleteRequest = DeleteRequest()
            .index(INDEX_NAME)
            .id(id)
        val actionFuture = client.delete(deleteRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        if (response.result != DocWriteResponse.Result.DELETED) {
            log.warn("$LOG_PREFIX:deleteObservabilityObject failed for $id; response:$response")
        }
        return response.result == DocWriteResponse.Result.DELETED
    }

    fun deleteObservabilityObjects(ids: Set<String>): Map<String, RestStatus> {
        createIndex()
        val bulkRequest = BulkRequest()
        ids.forEach {
            val deleteRequest = DeleteRequest()
                .index(INDEX_NAME)
                .id(it)
            bulkRequest.add(deleteRequest)
        }
        val actionFuture = client.bulk(bulkRequest)
        val response = actionFuture.actionGet(PluginSettings.operationTimeoutMs)
        val mutableMap = mutableMapOf<String, RestStatus>()
        response.forEach {
            mutableMap[it.id] = it.status()
            if (it.isFailed) {
                log.warn("$LOG_PREFIX:deleteObservabilityObjects failed for ${it.id}; response:${it.failureMessage}")
            }
        }
        return mutableMap
    }
}
