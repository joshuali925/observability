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

package org.opensearch.observability.action

import org.opensearch.OpenSearchStatusException
import org.opensearch.commons.authuser.User
import org.opensearch.observability.ObservabilityPlugin.Companion.LOG_PREFIX
import org.opensearch.observability.index.NotebooksIndex
import org.opensearch.observability.model.CreateObservabilityObjectRequest
import org.opensearch.observability.model.CreateObservabilityObjectResponse
import org.opensearch.observability.model.DeleteObservabilityObjectRequest
import org.opensearch.observability.model.DeleteObservabilityObjectResponse
import org.opensearch.observability.model.GetObservabilityObjectRequest
import org.opensearch.observability.model.GetObservabilityObjectResponse
import org.opensearch.observability.model.ObservabilityObjectDoc
import org.opensearch.observability.model.ObservabilityObjectSearchResult
import org.opensearch.observability.model.UpdateObservabilityObjectRequest
import org.opensearch.observability.model.UpdateObservabilityObjectResponse
import org.opensearch.observability.security.UserAccessManager
import org.opensearch.observability.util.logger
import org.opensearch.rest.RestStatus
import java.time.Instant

/**
 * Notebook index operation actions.
 */
internal object NotebookActions {
    private val log by logger(NotebookActions::class.java)

    /**
     * Create new notebook
     * @param request [CreateObservabilityObjectRequest] object
     * @return [CreateObservabilityObjectResponse]
     */
    fun create(request: CreateObservabilityObjectRequest, user: User?): CreateObservabilityObjectResponse {
        log.info("$LOG_PREFIX:Notebook-create")
        UserAccessManager.validateUser(user)
        val currentTime = Instant.now()
        val objectDoc = ObservabilityObjectDoc(
            currentTime,
            currentTime,
            UserAccessManager.getUserTenant(user),
            UserAccessManager.getAllAccessInfo(user),
            request.type,
            request.objectData
        )
        val docId = NotebooksIndex.createObservabilityObject(objectDoc)
        docId ?: throw OpenSearchStatusException(
            "Notebook Creation failed",
            RestStatus.INTERNAL_SERVER_ERROR
        )
        return CreateObservabilityObjectResponse(docId)
    }

    /**
     * Update Notebook
     * @param request [UpdateObservabilityObjectRequest] object
     * @return [UpdateObservabilityObjectResponse]
     */
    fun update(request: UpdateObservabilityObjectRequest, user: User?): UpdateObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-update ${request.objectId}")
        UserAccessManager.validateUser(user)
        val observabilityObject = NotebooksIndex.getObservabilityObject(request.objectId)
        observabilityObject
            ?: throw OpenSearchStatusException(
                "ObservabilityObject ${request.objectId} not found",
                RestStatus.NOT_FOUND
            )
        val currentDoc = observabilityObject.observabilityObjectDoc
        if (!UserAccessManager.doesUserHasAccess(user, currentDoc.tenant, currentDoc.access)) {
            throw OpenSearchStatusException(
                "Permission denied for ObservabilityObject ${request.objectId}",
                RestStatus.FORBIDDEN
            )
        }
        val currentTime = Instant.now()
        val objectDoc = ObservabilityObjectDoc(
            currentTime,
            currentDoc.createdTime,
            UserAccessManager.getUserTenant(user),
            UserAccessManager.getAllAccessInfo(user),
            request.type,
            request.objectData
        )
        if (!NotebooksIndex.updateObservabilityObject(request.objectId, objectDoc)) {
            throw OpenSearchStatusException("ObservabilityObject Update failed", RestStatus.INTERNAL_SERVER_ERROR)
        }
        return UpdateObservabilityObjectResponse(request.objectId)
    }

    /**
     * Get ObservabilityObject info
     * @param request [GetObservabilityObjectRequest] object
     * @return [GetObservabilityObjectResponse]
     */
    fun get(request: GetObservabilityObjectRequest, user: User?): GetObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-get ${request.objectIds}")
        UserAccessManager.validateUser(user)
        return when (request.objectIds.size) {
            0 -> getAll(request, user)
            1 -> info(request.objectIds.first(), user)
            else -> info(request.objectIds, user)
        }
    }

    /**
     * Get ObservabilityObject info
     * @param objectId object id
     * @param user the user info object
     * @return [GetObservabilityObjectResponse]
     */
    private fun info(objectId: String, user: User?): GetObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-info $objectId")
        val observabilityObjectDocInfo = NotebooksIndex.getObservabilityObject(objectId)
        observabilityObjectDocInfo
            ?: run {
                throw OpenSearchStatusException("ObservabilityObject $objectId not found", RestStatus.NOT_FOUND)
            }
        val currentDoc = observabilityObjectDocInfo.observabilityObjectDoc
        if (!UserAccessManager.doesUserHasAccess(user, currentDoc.tenant, currentDoc.access)) {
            throw OpenSearchStatusException("Permission denied for ObservabilityObject $objectId", RestStatus.FORBIDDEN)
        }
        val docInfo = ObservabilityObjectDoc(
            currentDoc.updatedTime,
            currentDoc.createdTime,
            currentDoc.tenant,
            currentDoc.access,
            currentDoc.type,
            currentDoc.objectData
        )
        return GetObservabilityObjectResponse(ObservabilityObjectSearchResult(docInfo))
    }

    /**
     * Get ObservabilityObject info
     * @param objectIds object id set
     * @param user the user info object
     * @return [GetObservabilityObjectResponse]
     */
    private fun info(objectIds: Set<String>, user: User?): GetObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-info $objectIds")
        val objectDocs = NotebooksIndex.getObservabilityObjects(objectIds)
        if (objectDocs.size != objectIds.size) {
            val mutableSet = objectIds.toMutableSet()
            objectDocs.forEach { mutableSet.remove(it.id) }
            throw OpenSearchStatusException(
                "ObservabilityObject $mutableSet not found",
                RestStatus.NOT_FOUND
            )
        }
        objectDocs.forEach {
            val currentDoc = it.observabilityObjectDoc
            if (!UserAccessManager.doesUserHasAccess(user, currentDoc.tenant, currentDoc.access)) {
                throw OpenSearchStatusException(
                    "Permission denied for ObservabilityObject ${it.id}",
                    RestStatus.FORBIDDEN
                )
            }
        }
        val configSearchResult = objectDocs.map {
            ObservabilityObjectDoc(
                it.observabilityObjectDoc.updatedTime,
                it.observabilityObjectDoc.createdTime,
                it.observabilityObjectDoc.tenant,
                it.observabilityObjectDoc.access,
                it.observabilityObjectDoc.type,
                it.observabilityObjectDoc.objectData
            )
        }
        return GetObservabilityObjectResponse(ObservabilityObjectSearchResult(configSearchResult))
    }

    /**
     * Get all ObservabilityObject matching the criteria
     * @param request [GetObservabilityObjectRequest] object
     * @param user the user info object
     * @return [GetObservabilityObjectResponse]
     */
    private fun getAll(request: GetObservabilityObjectRequest, user: User?): GetObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-getAll")
        val searchResult = NotebooksIndex.getAllObservabilityObjects(
            UserAccessManager.getUserTenant(user),
            UserAccessManager.getSearchAccessInfo(user),
            request
        )
        return GetObservabilityObjectResponse(searchResult)
    }

    /**
     * Delete ObservabilityObject
     * @param request [DeleteObservabilityObjectRequest] object
     * @param user the user info object
     * @return [DeleteObservabilityObjectResponse]
     */
    fun delete(request: DeleteObservabilityObjectRequest, user: User?): DeleteObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-delete ${request.objectIds}")
        return if (request.objectIds.size == 1) {
            delete(request.objectIds.first(), user)
        } else {
            delete(request.objectIds, user)
        }
    }

    /**
     * Delete Notebook
     * @param request [DeleteObservabilityObjectRequest] object
     * @return [DeleteObservabilityObjectResponse]
     */
    private fun delete(objectId: String, user: User?): DeleteObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-delete $objectId")
        UserAccessManager.validateUser(user)
        val observabilityObjectDocInfo = NotebooksIndex.getObservabilityObject(objectId)
        observabilityObjectDocInfo
            ?: run {
                throw OpenSearchStatusException(
                    "ObservabilityObject $objectId not found",
                    RestStatus.NOT_FOUND
                )
            }

        val currentDoc = observabilityObjectDocInfo.observabilityObjectDoc
        if (!UserAccessManager.doesUserHasAccess(user, currentDoc.tenant, currentDoc.access)) {
            throw OpenSearchStatusException(
                "Permission denied for ObservabilityObject $objectId",
                RestStatus.FORBIDDEN
            )
        }
        if (!NotebooksIndex.deleteObservabilityObject(objectId)) {
            throw OpenSearchStatusException(
                "ObservabilityObject $objectId delete failed",
                RestStatus.REQUEST_TIMEOUT
            )
        }
        return DeleteObservabilityObjectResponse(mapOf(Pair(objectId, RestStatus.OK)))
    }

    /**
     * Delete ObservabilityObject
     * @param objectIds ObservabilityObject object ids
     * @param user the user info object
     * @return [DeleteObservabilityObjectResponse]
     */
    private fun delete(objectIds: Set<String>, user: User?): DeleteObservabilityObjectResponse {
        log.info("$LOG_PREFIX:ObservabilityObject-delete $objectIds")
        UserAccessManager.validateUser(user)
        val configDocs = NotebooksIndex.getObservabilityObjects(objectIds)
        if (configDocs.size != objectIds.size) {
            val mutableSet = objectIds.toMutableSet()
            configDocs.forEach { mutableSet.remove(it.id) }
            throw OpenSearchStatusException(
                "ObservabilityObject $mutableSet not found",
                RestStatus.NOT_FOUND
            )
        }
        configDocs.forEach {
            val currentDoc = it.observabilityObjectDoc
            if (!UserAccessManager.doesUserHasAccess(user, currentDoc.tenant, currentDoc.access)) {
                throw OpenSearchStatusException(
                    "Permission denied for ObservabilityObject ${it.id}",
                    RestStatus.FORBIDDEN
                )
            }
        }
        val deleteStatus = NotebooksIndex.deleteObservabilityObjects(objectIds)
        return DeleteObservabilityObjectResponse(deleteStatus)
    }
}
