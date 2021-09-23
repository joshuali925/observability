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

package org.opensearch.observability.index

import org.opensearch.OpenSearchStatusException
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.QueryBuilder
import org.opensearch.index.query.QueryBuilders
import org.opensearch.index.query.Operator
import org.opensearch.observability.model.ObservabilityObjectType
import org.opensearch.observability.model.RestTag.CREATED_TIME_FIELD
import org.opensearch.observability.model.RestTag.NAME_FIELD
import org.opensearch.observability.model.RestTag.QUERY_FIELD
import org.opensearch.observability.model.RestTag.UPDATED_TIME_FIELD
import org.opensearch.rest.RestStatus

/**
 * Helper class for Get operations.
 */
internal class ObservabilityQueryHelper(val keyPrefix: ObservabilityObjectType) {
    companion object {
        private val METADATA_RANGE_FIELDS = setOf(
            UPDATED_TIME_FIELD,
            CREATED_TIME_FIELD
        )

        // keyword and text fields are under observability object and should be prepended with keyPrefix
        private val KEYWORD_FIELDS: Set<String> = setOf()
        private val TEXT_FIELDS = setOf(
            NAME_FIELD
        )

        private val METADATA_FIELDS = METADATA_RANGE_FIELDS
        private val OBSERVABILITY_OBJECT_FIELDS = KEYWORD_FIELDS.union(TEXT_FIELDS)
        private val ALL_FIELDS = METADATA_FIELDS.union(OBSERVABILITY_OBJECT_FIELDS)

        val FILTER_PARAMS = ALL_FIELDS.union(setOf(QUERY_FIELD))
    }

    fun getSortField(sortField: String?): String {
        return if (sortField == null) {
            UPDATED_TIME_FIELD
        } else {
            when {
                METADATA_RANGE_FIELDS.contains(sortField) -> "$sortField"
                KEYWORD_FIELDS.contains(sortField) -> "$keyPrefix.$sortField"
                TEXT_FIELDS.contains(sortField) -> "$keyPrefix.$sortField.keyword"
                else -> throw OpenSearchStatusException("Sort on $sortField not acceptable", RestStatus.NOT_ACCEPTABLE)
            }
        }
    }

    fun addQueryFilters(query: BoolQueryBuilder, filterParams: Map<String, String>) {
        println("debughere3")
        println(filterParams)
        filterParams.forEach {
            when {
                QUERY_FIELD == it.key -> query.filter(getQueryAllBuilder(it.value)) // all text search
                METADATA_RANGE_FIELDS.contains(it.key) -> query.filter(getRangeQueryBuilder(it.key, it.value))
                KEYWORD_FIELDS.contains(it.key) -> query.filter(getTermsQueryBuilder(it.key, it.value))
                TEXT_FIELDS.contains(it.key) -> query.filter(getMatchQueryBuilder(it.key, it.value))
                else -> throw OpenSearchStatusException("Query on ${it.key} not acceptable", RestStatus.NOT_ACCEPTABLE)
            }
        }
    }

    private fun getQueryAllBuilder(queryValue: String): QueryBuilder {
        val allQuery = QueryBuilders.queryStringQuery(queryValue)
        // Searching on metadata field is not supported. skip adding METADATA_FIELDS
        OBSERVABILITY_OBJECT_FIELDS.forEach {
            allQuery.field("$keyPrefix.$it")
        }
        return allQuery
    }

    private fun getRangeQueryBuilder(queryKey: String, queryValue: String): QueryBuilder {
        val range = queryValue.split("..")
        return when (range.size) {
            1 -> QueryBuilders.termQuery(queryKey, queryValue)
            2 -> {
                val rangeQuery = QueryBuilders.rangeQuery(queryKey)
                rangeQuery.from(range[0])
                rangeQuery.to(range[1])
                rangeQuery
            }
            else -> {
                throw OpenSearchStatusException(
                    "Invalid Range format $queryValue, allowed format 'exact' or 'from..to'",
                    RestStatus.NOT_ACCEPTABLE
                )
            }
        }
    }

    private fun getTermQueryBuilder(queryKey: String, queryValue: String): QueryBuilder {
        return QueryBuilders.termQuery("$keyPrefix.$queryKey", queryValue)
    }

    private fun getTermsQueryBuilder(queryKey: String, queryValue: String): QueryBuilder {
        return QueryBuilders.termsQuery("$keyPrefix.$queryKey", queryValue.split(","))
    }

    private fun getMatchQueryBuilder(queryKey: String, queryValue: String): QueryBuilder {
        return QueryBuilders.matchQuery("$keyPrefix.$queryKey", queryValue).operator(Operator.AND)
    }
}
