/*
 * Copyright 2019 Expedia, Inc
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

package com.expediagroup.graphql.execution

import com.expediagroup.graphql.annotations.GraphQLName
import com.fasterxml.jackson.annotation.JsonProperty
import graphql.GraphQLException
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FunctionDataFetcherTest {

    internal class MyContext(val value: String) : GraphQLContext

    internal class MyClass {
        fun print(string: String) = string

        fun printArray(items: Array<String>) = items.joinToString(separator = ":")

        fun printList(items: List<String>) = items.joinToString(separator = ":")

        fun contextClass(myContext: MyContext) = myContext.value

        fun dataFetchingEnvironment(environment: DataFetchingEnvironment): String = environment.field.name

        suspend fun suspendPrint(string: String): String = coroutineScope {
            string
        }

        fun throwException() { throw GraphQLException("Test Exception") }

        suspend fun suspendThrow(): String = coroutineScope<String> {
            throw GraphQLException("Suspended Exception")
        }

        @GraphQLName("myCustomField")
        fun renamedFields(@GraphQLName("myCustomArgument") arg: MyInputClass) = "You sent ${arg.field1}"

        fun resultList(items: List<String>) = Result.success(items.joinToString(separator = ":"))

        suspend fun suspendResultList(items: List<String>) = Result.success(items.joinToString(separator = ":"))

        fun resultFailure() = Result.failure<String>(GraphQLException("Failed to produce result"))

        suspend fun suspendResultFailure() = Result.failure<String>(GraphQLException("Failed to produce result"))
    }

    @GraphQLName("MyInputClassRenamed")
    internal data class MyInputClass(
        @JsonProperty("jacksonField")
        @GraphQLName("jacksonField")
        val field1: String
    )

    @Test
    fun `null target and null source returns null`() {
        val dataFetcher = FunctionDataFetcher(target = null, fn = MyClass::print)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.getSource<Any>() } returns null
        assertNull(dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `null target and valid source returns the value`() {
        val dataFetcher = FunctionDataFetcher(target = null, fn = MyClass::print)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.getSource<Any>() } returns MyClass()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")
        assertEquals(expected = "hello", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `valid target and null source returns the value`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::print)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")
        assertEquals(expected = "hello", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `valid target with context class`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::contextClass)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.getContext<MyContext>() } returns MyContext("foo")
        assertEquals(expected = "foo", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `array inputs can be converted by the object mapper`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::printArray)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to arrayOf("foo", "bar"))
        assertEquals(expected = "foo:bar", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `list can be converted by the object mapper`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::printList)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to listOf("foo", "bar"))

        assertEquals(expected = "foo:bar", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `dataFetchingEnvironement is passed as an argument`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::dataFetchingEnvironment)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.field } returns mockk {
            every { name } returns "fooBarBaz"
        }
        assertEquals(expected = "fooBarBaz", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `suspend functions return value wrapped in CompletableFuture`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::suspendPrint)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")

        val result = dataFetcher.get(mockEnvironmet)

        assertTrue(result is CompletableFuture<*>)
        assertEquals(expected = "hello", actual = result.get())
    }

    @Test
    fun `throwException function propagates the original exception`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::throwException)
        val mockEnvironmet: DataFetchingEnvironment = mockk()

        try {
            dataFetcher.get(mockEnvironmet)
            assertFalse(true, "Should not be here")
        } catch (e: Exception) {
            assertEquals(e.message, "Test Exception")
        }
    }

    @Test
    fun `suspendThrow throws exception when resolved`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::suspendThrow)
        val mockEnvironmet: DataFetchingEnvironment = mockk()

        try {
            val result = dataFetcher.get(mockEnvironmet)
            assertTrue(result is CompletableFuture<*>)
            result.get()
            assertFalse(true, "Should not be here")
        } catch (e: Exception) {
            val message = e.message
            assertNotNull(message)
            assertTrue(message.endsWith("Suspended Exception"), "Exception from function is not returned")
        }
    }

    @Test
    fun `renamed fields can be converted by the object mapper`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::renamedFields)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        val arguments = mapOf("myCustomArgument" to mapOf("jacksonField" to "foo"))
        every { mockEnvironmet.arguments } returns arguments
        assertEquals(expected = "You sent foo", actual = dataFetcher.get(mockEnvironmet))

    @Test
    fun `resultList returns a successful result when resolved`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::resultList)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to listOf("foo", "bar"))
        val result = dataFetcher.get(mockEnvironmet)
        assertTrue(result is Result<*>)
        assertEquals(result.getOrNull(), "foo:bar")
    }

    @Test
    fun `resultFailure returns a failing result when resolved`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::resultFailure)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to listOf("foo", "bar"))
        val result = dataFetcher.get(mockEnvironmet)
        assertTrue(result is Result<*>)
        assertEquals(result.exceptionOrNull()?.message, "Some Error")
    }

    @Test
    fun `suspendResultList returns a successful result when resolved`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::suspendResultList)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to listOf("foo", "bar"))

        val result = runBlocking { dataFetcher.get(mockEnvironmet) }
        assertTrue(result is Result<*>)
        assertEquals(result.getOrNull(), "foo:bar")
    }

    @Test
    fun `suspendResultFailure returns a failing result when resolved`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::suspendResultFailure)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to listOf("foo", "bar"))
        val result = runBlocking { dataFetcher.get(mockEnvironmet) }
        assertTrue(result is Result<*>)
        assertEquals(result.exceptionOrNull()?.message, "Some Error")
    }
}
