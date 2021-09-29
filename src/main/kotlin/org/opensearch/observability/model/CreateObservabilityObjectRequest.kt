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

import org.opensearch.action.ActionRequest
import org.opensearch.action.ActionRequestValidationException
import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.io.stream.Writeable
import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.ToXContentObject
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.notifications.action.CreateNotificationConfigResponse
import org.opensearch.commons.utils.fieldIfNotNull
import org.opensearch.commons.utils.logger
import org.opensearch.observability.model.RestTag.ID_FIELD
import org.opensearch.observability.model.RestTag.NOTEBOOK_FIELD
import java.io.IOException

/**
 * Action request for creating new configuration.
 */
internal class CreateObservabilityObjectRequest : ActionRequest, ToXContentObject {
    val objectId: String?
    val objectData: BaseObjectData?

    companion object {
        private val log by logger(CreateNotificationConfigResponse::class.java)

        /**
         * reader to create instance of class from writable.
         */
        val reader = Writeable.Reader { CreateObservabilityObjectRequest(it) }

        /**
         * Creator used in REST communication.
         * @param parser XContentParser to deserialize data from.
         * @param id optional id to use if missed in XContent
         */
        @JvmStatic
        @Throws(IOException::class)
        fun parse(parser: XContentParser, id: String? = null): CreateObservabilityObjectRequest {
            var objectId: String? = id
            var baseObjectData: BaseObjectData? = null

            XContentParserUtils.ensureExpectedToken(
                XContentParser.Token.START_OBJECT,
                parser.currentToken(),
                parser
            )
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    else -> {
                        println("[ObservabilityObject] in object parsing field $fieldName")
                        val objectTypeForTag = ObservabilityObjectType.fromTagOrDefault(fieldName)
                        if (objectTypeForTag != ObservabilityObjectType.NONE && baseObjectData == null) {
                            baseObjectData =
                                ObservabilityObjectDataProperties.createObjectData(objectTypeForTag, parser)
                        } else {
                            parser.skipChildren()
                            log.info("Unexpected field: $fieldName, while parsing CreateObservabilityObjectRequest")
                        }
                    }
                }
            }
//            if (configId != null) {
//                validateId(configId)
//            }
            baseObjectData ?: throw IllegalArgumentException("Object data field absent")
            return CreateObservabilityObjectRequest(baseObjectData, objectId)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        builder!!
        return builder.startObject()
            .fieldIfNotNull(ID_FIELD, objectId)
            .field(NOTEBOOK_FIELD, objectData)
            .endObject()
    }

    /**
     * constructor for creating the class
     * @param objectData the notification config object
     * @param objectId optional id to use for notification config object
     */
    constructor(objectData: BaseObjectData, objectId: String? = null) {
        this.objectData = objectData
        this.objectId = objectId
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    constructor(input: StreamInput) : super(input) {
        objectId = input.readOptionalString()
        objectData = input.readOptionalWriteable(
            ObservabilityObjectDataProperties.getReaderForObjectType(
                input.readEnum(
                    ObservabilityObjectType::class.java
                )
            )
        )
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun writeTo(output: StreamOutput) {
        super.writeTo(output)
        output.writeOptionalString(objectId)
        output.writeOptionalWriteable(objectData)
    }

    /**
     * {@inheritDoc}
     */
    override fun validate(): ActionRequestValidationException? {
        return null
    }
}
