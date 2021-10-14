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

package org.opensearch.observability.rest

import org.junit.Assert
import org.opensearch.observability.ObservabilityPlugin.Companion.BASE_OBSERVABILITY_URI
import org.opensearch.observability.PluginRestTestCase
import org.opensearch.observability.constructNotebookRequest
import org.opensearch.observability.jsonify
import org.opensearch.observability.validateErrorResponse
import org.opensearch.observability.validateTimeRecency
import org.opensearch.rest.RestRequest
import org.opensearch.rest.RestStatus
import java.time.Instant

class ObservabilityIT : PluginRestTestCase() {
    private fun createNotebook(name: String = "test"): String {
        val notebookCreateRequest = constructNotebookRequest(name)
        val notebookCreateResponse = executeRequest(
            RestRequest.Method.POST.name,
            "$BASE_OBSERVABILITY_URI/object",
            notebookCreateRequest,
            RestStatus.OK.status
        )
        val notebookId = notebookCreateResponse.get("objectId").asString
        Assert.assertNotNull("notebookId should be generated", notebookId)
        Thread.sleep(100)
        return notebookId
    }

    fun `test create notebook`() {
        createNotebook()

        val notebookInvalidCreateResponse = executeRequest(
            RestRequest.Method.POST.name,
            "$BASE_OBSERVABILITY_URI/object",
            "",
            RestStatus.BAD_REQUEST.status
        )
        validateErrorResponse(notebookInvalidCreateResponse, RestStatus.BAD_REQUEST.status, "parse_exception")
        Thread.sleep(100)
    }

    fun `test get notebook`() {
        val notebookCreateRequest = constructNotebookRequest()
        val notebookCreateResponse = executeRequest(
            RestRequest.Method.POST.name,
            "$BASE_OBSERVABILITY_URI/object",
            notebookCreateRequest,
            RestStatus.OK.status
        )
        val notebookId = notebookCreateResponse.get("objectId").asString
        Assert.assertNotNull("notebookId should be generated", notebookId)
        Thread.sleep(100)

        val notebooksGetResponse = executeRequest(
            RestRequest.Method.GET.name,
            "$BASE_OBSERVABILITY_URI/object/$notebookId",
            "",
            RestStatus.OK.status
        )
        val notebookDetails = notebooksGetResponse.get("observabilityObjectList").asJsonArray.get(0).asJsonObject
        Assert.assertEquals(notebookId, notebookDetails.get("objectId").asString)
        Assert.assertEquals(
            jsonify(notebookCreateRequest).get("notebook").asJsonObject,
            notebookDetails.get("notebook").asJsonObject
        )
        validateTimeRecency(Instant.ofEpochMilli(notebookDetails.get("lastUpdatedTimeMs").asLong))
        validateTimeRecency(Instant.ofEpochMilli(notebookDetails.get("createdTimeMs").asLong))
        Thread.sleep(100)

        val notebooksInvalidGetResponse = executeRequest(
            RestRequest.Method.GET.name,
            "$BASE_OBSERVABILITY_URI/object/invalid-id",
            "",
            RestStatus.NOT_FOUND.status
        )
        validateErrorResponse(notebooksInvalidGetResponse, RestStatus.NOT_FOUND.status)
        Thread.sleep(100)
    }

    fun `test update notebook`() {
        val notebookId = createNotebook()

        val newName = "updated_name"
        val notebookUpdateRequest = constructNotebookRequest(newName)
        val notebookUpdateResponse = executeRequest(
            RestRequest.Method.PUT.name,
            "$BASE_OBSERVABILITY_URI/object/$notebookId",
            notebookUpdateRequest,
            RestStatus.OK.status
        )
        Assert.assertNotNull(notebookId, notebookUpdateResponse.get("objectId").asString)
        Thread.sleep(100)

        val notebooksGetResponse = executeRequest(
            RestRequest.Method.GET.name,
            "$BASE_OBSERVABILITY_URI/object/$notebookId",
            "",
            RestStatus.OK.status
        )
        val notebookDetails = notebooksGetResponse.get("observabilityObjectList").asJsonArray.get(0).asJsonObject
        Assert.assertEquals(notebookId, notebookDetails.get("objectId").asString)
        Assert.assertEquals(
            newName,
            notebookDetails.get("notebook").asJsonObject.get("name").asString
        )
        Thread.sleep(100)

        val notebooksInvalidUpdateResponse = executeRequest(
            RestRequest.Method.PUT.name,
            "$BASE_OBSERVABILITY_URI/object/invalid-id",
            notebookUpdateRequest,
            RestStatus.NOT_FOUND.status
        )
        validateErrorResponse(notebooksInvalidUpdateResponse, RestStatus.NOT_FOUND.status)
        Thread.sleep(100)
    }

    fun `test delete notebook`() {
        val notebookId = createNotebook()

        val notebookDeleteResponse = executeRequest(
            RestRequest.Method.DELETE.name,
            "$BASE_OBSERVABILITY_URI/object/$notebookId",
            "",
            RestStatus.OK.status
        )
        Assert.assertEquals(
            "OK",
            notebookDeleteResponse.get("deleteResponseList").asJsonObject.get(notebookId).asString
        )
        Thread.sleep(100)

        val notebookInvalidDeleteResponse = executeRequest(
            RestRequest.Method.DELETE.name,
            "$BASE_OBSERVABILITY_URI/object/$notebookId",
            "",
            RestStatus.NOT_FOUND.status
        )
        validateErrorResponse(notebookInvalidDeleteResponse, RestStatus.NOT_FOUND.status)
        Thread.sleep(100)
    }

    fun `test get all notebooks`() {
        val notebooksGetAllEmptyResponse = executeRequest(
            RestRequest.Method.GET.name,
            "$BASE_OBSERVABILITY_URI/object",
            "",
            RestStatus.OK.status
        )
        Assert.assertEquals(0, notebooksGetAllEmptyResponse.get("totalHits").asInt)

        val notebookIds = Array(5) { createNotebook("test-$it") }
        Thread.sleep(1000)

        val notebooksGetAllResponse = executeRequest(
            RestRequest.Method.GET.name,
            "$BASE_OBSERVABILITY_URI/object",
            "",
            RestStatus.OK.status
        )
        Assert.assertEquals(5, notebooksGetAllResponse.get("totalHits").asInt)
        val notebooksList = notebooksGetAllResponse.get("observabilityObjectList").asJsonArray
        Assert.assertArrayEquals(
            notebookIds,
            notebooksList.map { it.asJsonObject.get("objectId").asString }.toTypedArray()
        )
    }
}
