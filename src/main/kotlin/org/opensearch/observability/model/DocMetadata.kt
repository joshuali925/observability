package org.opensearch.observability.model

import org.opensearch.common.xcontent.ToXContent
import org.opensearch.common.xcontent.XContentBuilder
import org.opensearch.common.xcontent.XContentParser
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.utils.logger
import org.opensearch.commons.utils.stringList
import org.opensearch.observability.model.RestTag.ACCESS_LIST_FIELD
import org.opensearch.observability.model.RestTag.CREATED_TIME_FIELD
import org.opensearch.observability.model.RestTag.TENANT_FIELD
import org.opensearch.observability.model.RestTag.UPDATED_TIME_FIELD
import org.opensearch.observability.security.UserAccessManager.DEFAULT_TENANT
import java.time.Instant

/**
 * Class for storing document metadata that are not exposed to external entities.
 */
data class DocMetadata(
    val updatedTime: Instant,
    val createdTime: Instant,
    val tenant: String,
    val access: List<String> // "User:user", "Role:sample_role", "BERole:sample_backend_role"
) : ToXContent {
    companion object {
        private val log by logger(DocMetadata::class.java)

        /**
         * Parse the data from parser and create object
         * @param parser data referenced at parser
         * @return created object
         */
        fun parse(parser: XContentParser): DocMetadata {
            var updatedTime: Instant? = null
            var createdTime: Instant? = null
            var tenant: String? = null
            var access: List<String> = listOf()
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser)
            while (XContentParser.Token.END_OBJECT != parser.nextToken()) {
                val fieldName = parser.currentName()
                parser.nextToken()
                when (fieldName) {
                    UPDATED_TIME_FIELD -> updatedTime = Instant.ofEpochMilli(parser.longValue())
                    CREATED_TIME_FIELD -> createdTime = Instant.ofEpochMilli(parser.longValue())
                    TENANT_FIELD -> tenant = parser.text()
                    ACCESS_LIST_FIELD -> access = parser.stringList()
                    else -> {
                        parser.skipChildren()
                        log.info("DocMetadata Skipping Unknown field $fieldName")
                    }
                }
            }
            updatedTime ?: throw IllegalArgumentException("$UPDATED_TIME_FIELD field absent")
            createdTime ?: throw IllegalArgumentException("$CREATED_TIME_FIELD field absent")
            tenant = tenant ?: DEFAULT_TENANT
            return DocMetadata(
                updatedTime,
                createdTime,
                tenant,
                access
            )
        }
    }

    /**
     * {ref toXContent}
     */
    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        return builder!!.startObject()
            .field(UPDATED_TIME_FIELD, updatedTime.toEpochMilli())
            .field(CREATED_TIME_FIELD, createdTime.toEpochMilli())
            .field(TENANT_FIELD, tenant)
            .field(ACCESS_LIST_FIELD, access)
            .endObject()
    }
}
