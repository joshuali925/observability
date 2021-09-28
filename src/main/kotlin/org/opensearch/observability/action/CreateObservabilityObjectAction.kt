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

package org.opensearch.observability.action

import org.opensearch.action.ActionListener
import org.opensearch.action.ActionType
import org.opensearch.action.support.ActionFilters
import org.opensearch.client.Client
import org.opensearch.common.inject.Inject
import org.opensearch.common.xcontent.NamedXContentRegistry
import org.opensearch.commons.authuser.User
import org.opensearch.commons.utils.recreateObject
import org.opensearch.observability.index.NotebooksIndex
import org.opensearch.observability.model.CreateObservabilityObjectRequest
import org.opensearch.observability.model.CreateObservabilityObjectResponse
import org.opensearch.tasks.Task
import org.opensearch.transport.TransportService

/**
 * Create NotificationConfig transport action
 */
internal class CreateObservabilityObjectAction @Inject constructor(
    transportService: TransportService,
    client: Client,
    actionFilters: ActionFilters,
    val xContentRegistry: NamedXContentRegistry
) : PluginBaseAction<CreateObservabilityObjectRequest, CreateObservabilityObjectResponse>(
    NAME,
    transportService,
    client,
    actionFilters,
    ::CreateObservabilityObjectRequest
) {
    companion object {
        private const val NAME = "cluster:admin/opendistro/notebooks/create"
        internal val ACTION_TYPE = ActionType(NAME, ::CreateObservabilityObjectResponse)
    }

    /**
     * {@inheritDoc}
     * Transform the request and call super.doExecute() to support call from other plugins.
     */
    override fun doExecute(
        task: Task?,
        request: CreateObservabilityObjectRequest,
        listener: ActionListener<CreateObservabilityObjectResponse>
    ) {
        val transformedRequest = request as? CreateObservabilityObjectRequest
            ?: recreateObject(request) { CreateObservabilityObjectRequest(it) }
        super.doExecute(task, transformedRequest, listener)
    }

    /**
     * {@inheritDoc}
     */
    override fun executeRequest(
        request: CreateObservabilityObjectRequest,
        user: User?
    ): CreateObservabilityObjectResponse {
        return NotebooksIndex.create(request, user)
    }
}
