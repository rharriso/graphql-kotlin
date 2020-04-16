/**
 * Copyright 2020 Expedia, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.graphql.examples.spark.schema.models

import graphql.GraphQLException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderOptions
import java.util.concurrent.CompletionStage

const val UNIVERSITY_LOADER_NAME = "UNIVERSITY_LOADER"

private class BatchUniversityLoader : BatchLoader<Long, University?> {
    override fun load(ids: MutableList<Long>?): CompletionStage<MutableList<University?>> {
        return GlobalScope.future {
            ids ?: return@future mutableListOf<University?>()
            Thread.sleep(1000)
            val universityIdMap = University.search(ids.toList()).toMutableList().associateBy { it.id }
            ids.map { universityIdMap[it] }.toMutableList()
        }
    }
}

val universityDataLoader: DataLoader<Long, University?> =
    DataLoader.newDataLoader(BatchUniversityLoader(), DataLoaderOptions.newOptions().setCachingEnabled(false))

class University(val id: Long, val name: String? = null) {
    companion object {
        fun search(ids: List<Long>): List<University> {
            return listOf(
                University(id = 1, name = "University of Nebraska-Lincoln"),
                University(id = 2, name = "Kansas State University"),
                University(id = 3, name = "Purdue University"),
                University(id = 4, name = "Kennesaw State University"),
                University(id = 5, name = "University of Georgia")
            ).filter { ids.contains(it.id) }
        }
    }

    fun longThatNeverComes(): Long {
        throw GraphQLException("This value will never return")
    }
}
