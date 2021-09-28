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

import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.io.stream.Writeable
import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.utils.fieldIfNotNull
import org.opensearch.commons.utils.logger
import org.opensearch.observability.model.ObservabilityObjectDataProperties.createObjectData
import org.opensearch.observability.model.ObservabilityObjectDataProperties.getReaderForObjectType
import org.opensearch.observability.model.ObservabilityObjectDataProperties.validateObjectData
import org.opensearch.observability.model.RestTag.TYPE_FIELD
import java.io.IOException

/**
 * Data class representing Notification config.
 */
data class ObservabilityObject(
    val type: ObservabilityObjectType,
    val objectData: BaseObjectData?,
) : BaseModel {

    init {
        if (!validateObjectData(type, objectData)) {
            throw IllegalArgumentException("Type: $type and data doesn't match")
        }
        if (type === ObservabilityObjectType.NONE) {
            log.info("Some config field not recognized")
        }
    }

    companion object {
        private val log by logger(ObservabilityObject::class.java)

        /**
         * reader to create instance of class from writable.
         */
        val reader = Writeable.Reader { ObservabilityObject(it) }

        /**
         * Creator used in REST communication.
         * @param parser XContentParser to deserialize data from.
         */
        @Suppress("ComplexMethod")
        @JvmStatic
        @Throws(IOException::class)
        fun parse(parser: XContentParser): ObservabilityObject {
            var type: ObservabilityObjectType? = null
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
                    TYPE_FIELD -> type = ObservabilityObjectType.fromTagOrDefault(parser.text())
                    else -> {
                        println("[ObservabilityObject] in object parsing field $fieldName")
                        val objectTypeForTag = ObservabilityObjectType.fromTagOrDefault(fieldName)
                        if (objectTypeForTag != ObservabilityObjectType.NONE && baseObjectData == null) {
                            baseObjectData = createObjectData(objectTypeForTag, parser)
                        } else {
                            parser.skipChildren()
                            log.info("Unexpected field: $fieldName, while parsing ObservabilityObject")
                        }
                    }
                }
            }
//            type ?: throw IllegalArgumentException("$TYPE_FIELD field absent")
            type = ObservabilityObjectType.NOTEBOOK
            return ObservabilityObject(type, baseObjectData)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        builder!!
        return builder.startObject()
            .field(TYPE_FIELD, type.tag)
            .fieldIfNotNull(type.tag, objectData)
            .endObject()
    }

    /**
     * Constructor used in transport action communication.
     * @param input StreamInput stream to deserialize data from.
     */
    constructor(input: StreamInput) : this(
        type = input.readEnum(ObservabilityObjectType::class.java),
        objectData = input.readOptionalWriteable(getReaderForObjectType(input.readEnum(ObservabilityObjectType::class.java)))
    )

    /**
     * {@inheritDoc}
     */
    override fun writeTo(output: StreamOutput) {
        output.writeEnum(type)
        // Reading config types multiple times in constructor
        output.writeEnum(type)
        output.writeOptionalWriteable(objectData)
    }
}
