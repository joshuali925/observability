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

import org.opensearch.common.Strings
import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.io.stream.Writeable
import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.notifications.NotificationConstants.CONFIG_TYPE_TAG
import org.opensearch.commons.notifications.NotificationConstants.DESCRIPTION_TAG
import org.opensearch.commons.notifications.NotificationConstants.FEATURE_LIST_TAG
import org.opensearch.commons.notifications.NotificationConstants.IS_ENABLED_TAG
import org.opensearch.commons.notifications.NotificationConstants.NAME_TAG
import org.opensearch.commons.notifications.model.BaseConfigData
import org.opensearch.commons.notifications.model.config.ConfigDataProperties.createConfigData
import org.opensearch.commons.notifications.model.config.ConfigDataProperties.getReaderForConfigType
import org.opensearch.commons.utils.STRING_READER
import org.opensearch.commons.utils.STRING_WRITER
import org.opensearch.commons.utils.fieldIfNotNull
import org.opensearch.commons.utils.logger
import org.opensearch.commons.utils.stringList
import java.io.IOException

/**
 * Data class representing Notification config.
 */
data class ObservabilityObject(
//    val name: String,
    val type: ObservabilityObjectType,
    val observabilityObjectData: BaseObservabilityObjectData,
) : BaseModel {

    init {
//        require(!Strings.isNullOrEmpty(name)) { "name is null or empty" }
//        if (!validateConfigData(configType, configData)) {
//            throw IllegalArgumentException("ConfigType: $configType and data doesn't match")
//        }
        if (type === ObservabilityObjectType.NONE) {
            log.info("Some config field not recognized")
        }
    }

    companion object {
        private val log by logger(ObservabilityObject::class.java)
        private const val TYPE_TAG = "type"

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
            var observabilityObjectData: BaseObservabilityObjectData? = null
            XContentParserUtils.ensureExpectedToken(
                XContentParser.Token.START_OBJECT,
                parser.currentToken(),
                parser
            )
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    TYPE_TAG -> type = ObservabilityObjectType.fromTagOrDefault(parser.text())
                    else -> {
                        val configTypeForTag = ObservabilityObjectType.fromTagOrDefault(fieldName)
                        if (configTypeForTag != ObservabilityObjectType.NONE && observabilityObjectData == null) {
                            observabilityObjectData = createConfigData(configTypeForTag, parser)
                        } else {
                            parser.skipChildren()
                            log.info("Unexpected field: $fieldName, while parsing configuration")
                        }
                    }
                }
            }
            type ?: throw IllegalArgumentException("$TYPE_TAG field absent")
            return ObservabilityObject(
                type,
                observabilityObjectData
            )
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        builder!!
        return builder.startObject()
            .field(NAME_TAG, name)
            .field(DESCRIPTION_TAG, description)
            .field(CONFIG_TYPE_TAG, configType.tag)
            .field(FEATURE_LIST_TAG, features)
            .field(IS_ENABLED_TAG, isEnabled)
            .fieldIfNotNull(configType.tag, configData)
            .endObject()
    }

    /**
     * Constructor used in transport action communication.
     * @param input StreamInput stream to deserialize data from.
     */
    constructor(input: StreamInput) : this(
        name = input.readString(),
        description = input.readString(),
        configType = input.readEnum(ObservabilityObjectType::class.java),
        features = input.readSet(STRING_READER),
        isEnabled = input.readBoolean(),
        configData = input.readOptionalWriteable(getReaderForConfigType(input.readEnum(ObservabilityObjectType::class.java)))
    )

    /**
     * {@inheritDoc}
     */
    override fun writeTo(output: StreamOutput) {
        output.writeString(name)
        output.writeString(description)
        output.writeEnum(configType)
        output.writeCollection(features, STRING_WRITER)
        output.writeBoolean(isEnabled)
        // Reading config types multiple times in constructor
        output.writeEnum(configType)
        output.writeOptionalWriteable(configData)
    }
}
