package org.opensearch.observability.model

import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentFactory
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.utils.logger
import org.opensearch.observability.model.RestTag.METADATA_FIELD
import org.opensearch.observability.model.RestTag.OBJECT_FIELD
import java.io.IOException

/**
 * Data class representing Notification config.
 */
data class ObservabilityObjectDoc(
    val metadata: DocMetadata,
    val observabilityObject: ObservabilityObject
) : ToXContent {

    companion object {
        private val log by logger(ObservabilityObjectDoc::class.java)

        /**
         * Parse the data from parser and create object
         * @param parser data referenced at parser
         * @return created object
         */
        @JvmStatic
        @Throws(IOException::class)
        fun parse(parser: XContentParser): ObservabilityObjectDoc {
            var metadata: DocMetadata? = null
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
                    METADATA_FIELD -> metadata = DocMetadata.parse(parser)
                    OBJECT_FIELD -> observabilityObject = ObservabilityObject.parse(parser)
                    else -> {
                        parser.skipChildren()
                        log.info("Unexpected field: $fieldName, while parsing configuration doc")
                    }
                }
            }
            metadata ?: throw IllegalArgumentException("$METADATA_FIELD field absent")
            observabilityObject ?: throw IllegalArgumentException("$OBJECT_FIELD field absent")
            return ObservabilityObjectDoc(metadata, observabilityObject)
        }
    }

    /**
     * create XContentBuilder from this object using [XContentFactory.jsonBuilder()]
     * @param params XContent parameters
     * @return created XContentBuilder object
     */
    fun toXContent(params: ToXContent.Params = ToXContent.EMPTY_PARAMS): XContentBuilder {
        return toXContent(XContentFactory.jsonBuilder(), params)
    }

    /**
     * {@inheritDoc}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        builder!!
        return builder.startObject()
            .field(METADATA_FIELD, metadata)
            .field(OBJECT_FIELD, observabilityObject)
            .endObject()
    }
}
