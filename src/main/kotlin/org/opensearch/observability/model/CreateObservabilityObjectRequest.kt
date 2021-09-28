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
import org.opensearch.observability.model.RestTag.OBJECT_FIELD
import java.io.IOException

/**
 * Action request for creating new configuration.
 */
internal class CreateObservabilityObjectRequest : ActionRequest, ToXContentObject {
    val objectId: String?
    val observabilityObject: ObservabilityObject

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
            var observabilityObject: ObservabilityObject? = null

            XContentParserUtils.ensureExpectedToken(
                XContentParser.Token.START_OBJECT,
                parser.currentToken(),
                parser
            )
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    ID_FIELD -> objectId = parser.text()
                    OBJECT_FIELD -> observabilityObject = ObservabilityObject.parse(parser)
                    else -> {
                        parser.skipChildren()
                        log.info("Unexpected field: $fieldName, while parsing CreateObjectRequest")
                    }
                }
            }
            observabilityObject ?: throw IllegalArgumentException("${OBJECT_FIELD} field absent")
//            if (configId != null) {
//                validateId(configId)
//            }
            return CreateObservabilityObjectRequest(observabilityObject, objectId)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        builder!!
        return builder.startObject()
            .fieldIfNotNull(ID_FIELD, objectId)
            .field(OBJECT_FIELD, observabilityObject)
            .endObject()
    }

    /**
     * constructor for creating the class
     * @param observabilityObject the notification config object
     * @param configId optional id to use for notification config object
     */
    constructor(observabilityObject: ObservabilityObject, objectId: String? = null) {
        this.objectId = objectId
        this.observabilityObject = observabilityObject
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    constructor(input: StreamInput) : super(input) {
        objectId = input.readOptionalString()
        observabilityObject = ObservabilityObject.reader.read(input)!!
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun writeTo(output: StreamOutput) {
        super.writeTo(output)
        output.writeOptionalString(objectId)
        observabilityObject.writeTo(output)
    }

    /**
     * {@inheritDoc}
     */
    override fun validate(): ActionRequestValidationException? {
        return null
    }
}
