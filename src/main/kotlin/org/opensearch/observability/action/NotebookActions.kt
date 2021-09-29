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
import org.opensearch.commons.notifications.action.DeleteNotificationConfigRequest
import org.opensearch.commons.notifications.action.DeleteNotificationConfigResponse
import org.opensearch.observability.ObservabilityPlugin.Companion.LOG_PREFIX
import org.opensearch.observability.index.NotebooksIndex
import org.opensearch.observability.model.CreateObservabilityObjectRequest
import org.opensearch.observability.model.CreateObservabilityObjectResponse
import org.opensearch.observability.model.DeleteObservabilityObjectRequest
import org.opensearch.observability.model.DeleteObservabilityObjectResponse
import org.opensearch.observability.model.ObservabilityObjectDoc
import org.opensearch.observability.model.notebook.GetAllNotebooksRequest
import org.opensearch.observability.model.notebook.GetAllNotebooksResponse
import org.opensearch.observability.model.notebook.GetNotebookRequest
import org.opensearch.observability.model.notebook.GetNotebookResponse
import org.opensearch.observability.model.notebook.NotebookDetails
import org.opensearch.observability.model.notebook.UpdateNotebookRequest
import org.opensearch.observability.model.notebook.UpdateNotebookResponse
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
        val docId = NotebooksIndex.create(objectDoc)
        docId ?: throw OpenSearchStatusException(
            "Notebook Creation failed",
            RestStatus.INTERNAL_SERVER_ERROR
        )
        return CreateObservabilityObjectResponse(docId)
    }

    /**
     * Update Notebook
     * @param request [UpdateNotebookRequest] object
     * @return [UpdateNotebookResponse]
     */
    fun update(request: UpdateNotebookRequest, user: User?): UpdateNotebookResponse {
        log.info("$LOG_PREFIX:Notebook-update ${request.notebookId}")
        UserAccessManager.validateUser(user)
        val currentNotebookDetails = NotebooksIndex.getNotebook(request.notebookId)
        currentNotebookDetails
            ?: throw OpenSearchStatusException("Notebook ${request.notebookId} not found", RestStatus.NOT_FOUND)
        if (!UserAccessManager.doesUserHasAccess(user, currentNotebookDetails.tenant, currentNotebookDetails.access)) {
            throw OpenSearchStatusException(
                "Permission denied for Notebook ${request.notebookId}",
                RestStatus.FORBIDDEN
            )
        }
        val currentTime = Instant.now()
        val notebookDetails = NotebookDetails(
            request.notebookId,
            currentTime,
            currentNotebookDetails.createdTime,
            UserAccessManager.getUserTenant(user),
            currentNotebookDetails.access,
            request.notebook
        )
        if (!NotebooksIndex.updateNotebook(request.notebookId, notebookDetails)) {
            throw OpenSearchStatusException("Notebook Update failed", RestStatus.INTERNAL_SERVER_ERROR)
        }
        return UpdateNotebookResponse(request.notebookId)
    }

    /**
     * Get Notebook info
     * @param request [GetNotebookRequest] object
     * @return [GetNotebookResponse]
     */
    fun info(request: GetNotebookRequest, user: User?): GetNotebookResponse {
        log.info("$LOG_PREFIX:Notebook-info ${request.notebookId}")
        UserAccessManager.validateUser(user)
        val notebookDetails = NotebooksIndex.getNotebook(request.notebookId)
        notebookDetails
            ?: throw OpenSearchStatusException("Notebook ${request.notebookId} not found", RestStatus.NOT_FOUND)
        if (!UserAccessManager.doesUserHasAccess(user, notebookDetails.tenant, notebookDetails.access)) {
            throw OpenSearchStatusException(
                "Permission denied for Notebook ${request.notebookId}",
                RestStatus.FORBIDDEN
            )
        }
        return GetNotebookResponse(notebookDetails, UserAccessManager.hasAllInfoAccess(user))
    }

    /**
     * Delete NotificationConfig
     * @param request [DeleteNotificationConfigRequest] object
     * @param user the user info object
     * @return [DeleteNotificationConfigResponse]
     */
    fun delete(request: DeleteObservabilityObjectRequest, user: User?): DeleteObservabilityObjectResponse {
        log.info("$LOG_PREFIX:NotificationConfig-delete ${request.objectIds}")
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
    fun delete(objectId: String, user: User?): DeleteObservabilityObjectResponse {
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
     * Delete NotificationConfig
     * @param objectIds NotificationConfig object ids
     * @param user the user info object
     * @return [DeleteObservabilityObjectResponse]
     */
    private fun delete(objectIds: Set<String>, user: User?): DeleteObservabilityObjectResponse {
        log.info("$LOG_PREFIX:NotificationConfig-delete $objectIds")
        UserAccessManager.validateUser(user)
        val configDocs = NotebooksIndex.getObservabilityObjects(objectIds)
        if (configDocs.size != objectIds.size) {
            val mutableSet = objectIds.toMutableSet()
            configDocs.forEach { mutableSet.remove(it.id) }
            throw OpenSearchStatusException(
                "NotificationConfig $mutableSet not found",
                RestStatus.NOT_FOUND
            )
        }
        configDocs.forEach {
            val currentDoc = it.observabilityObjectDoc
            if (!UserAccessManager.doesUserHasAccess(user, currentDoc.tenant, currentDoc.access)) {
                throw OpenSearchStatusException(
                    "Permission denied for NotificationConfig ${it.id}",
                    RestStatus.FORBIDDEN
                )
            }
        }
        val deleteStatus = NotebooksIndex.deleteObservabilityObjects(objectIds)
        return DeleteObservabilityObjectResponse(deleteStatus)
    }

    /**
     * Get all Notebooks
     * @param request [GetAllNotebooksRequest] object
     * @return [GetAllNotebooksResponse]
     */
    fun getAll(request: GetAllNotebooksRequest, user: User?): GetAllNotebooksResponse {
        log.info("$LOG_PREFIX:Notebook-getAll fromIndex:${request.fromIndex} maxItems:${request.maxItems}")
        UserAccessManager.validateUser(user)
        val notebooksList = NotebooksIndex.getAllNotebooks(
            UserAccessManager.getUserTenant(user),
            UserAccessManager.getSearchAccessInfo(user),
            request
        )
        return GetAllNotebooksResponse(notebooksList, UserAccessManager.hasAllInfoAccess(user))
    }
}
