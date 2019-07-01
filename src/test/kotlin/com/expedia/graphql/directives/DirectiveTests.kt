package com.expedia.graphql.directives

import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.annotations.GraphQLDirective
import com.expedia.graphql.getTestSchemaConfigWithHooks
import com.expedia.graphql.hooks.SchemaGeneratorHooks
import com.expedia.graphql.testSchemaConfig
import com.expedia.graphql.toSchema
import graphql.Scalars
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DirectiveTests {

    @Test
    fun `SchemaGenerator marks deprecated fields in the return objects`() {
        val schema = toSchema(queries = listOf(TopLevelObject(QueryWithDeprecatedFields())), config = testSchemaConfig)
        val topLevelQuery = schema.getObjectType("Query")
        val query = topLevelQuery.getFieldDefinition("deprecatedFieldQuery")
        val result = (query.type as? GraphQLNonNull)?.wrappedType as? GraphQLObjectType
        val deprecatedField = result?.getFieldDefinition("deprecatedField")

        assertEquals(deprecatedField?.isDeprecated, true)
        assertEquals("this field is deprecated", deprecatedField?.deprecationReason)
    }

    @Test
    fun `SchemaGenerator marks deprecated queries and documents replacement`() {
        val schema = toSchema(queries = listOf(TopLevelObject(QueryWithDeprecatedFields())), config = testSchemaConfig)
        val topLevelQuery = schema.getObjectType("Query")
        val query = topLevelQuery.getFieldDefinition("deprecatedQueryWithReplacement")

        assertTrue(query.isDeprecated)
        assertEquals("this query is also deprecated, replace with shinyNewQuery", query.deprecationReason)
        assertTrue(query.directives.stream().anyMatch { it.name == "deprecated" })
    }

    @Test
    fun `SchemaGenerator marks deprecated queries`() {
        val schema = toSchema(queries = listOf(TopLevelObject(QueryWithDeprecatedFields())), config = testSchemaConfig)
        val topLevelQuery = schema.getObjectType("Query")
        val query = topLevelQuery.getFieldDefinition("deprecatedQuery")
        assertTrue(query.isDeprecated)
        assertEquals("this query is deprecated", query.deprecationReason)
        assertTrue(query.directives.stream().anyMatch { it.name == "deprecated" })
    }

    @Test
    fun `Default directive names are normalized`() {
        val wiring = object : KotlinSchemaDirectiveWiring {}
        val config = getTestSchemaConfigWithHooks(hooks = object : SchemaGeneratorHooks {
            override val wiringFactory: KotlinDirectiveWiringFactory
                get() = KotlinDirectiveWiringFactory(manualWiring = mapOf("dummyDirective" to wiring, "RightNameDirective" to wiring))
        })
        val schema = toSchema(queries = listOf(TopLevelObject(QueryObject())), config = config)

        val query = schema.queryType.getFieldDefinition("query")
        assertNotNull(query)
        assertNotNull(query.getDirective("dummyDirective"))
    }

    @Test
    fun `Custom directive names are not modified`() {
        val wiring = object : KotlinSchemaDirectiveWiring {}
        val config = getTestSchemaConfigWithHooks(hooks = object : SchemaGeneratorHooks {
            override val wiringFactory: KotlinDirectiveWiringFactory
                get() = KotlinDirectiveWiringFactory(manualWiring = mapOf("dummyDirective" to wiring, "RightNameDirective" to wiring))
        })
        val schema = toSchema(queries = listOf(TopLevelObject(QueryObject())), config = config)

        val directive = assertNotNull(
                (schema.getType("Location") as? GraphQLObjectType)
                        ?.getDirective("RightNameDirective")
        )

        assertEquals("arenaming", directive.arguments[0].value)
        assertEquals("arg", directive.arguments[0].name)
        assertEquals(GraphQLNonNull(Scalars.GraphQLString), directive.arguments[0].type)
    }
}

@GraphQLDirective(name = "RightNameDirective")
annotation class WrongNameDirective(val arg: String)

@GraphQLDirective
annotation class DummyDirective

class Geography(
    val id: Int?,
    val type: GeoType,
    val locations: List<Location>
) {
    @Suppress("Detekt.FunctionOnlyReturningConstant")
    fun somethingCool(): String = "Something cool"
}

enum class GeoType {
    CITY, STATE
}

@WrongNameDirective(arg = "arenaming")
data class Location(val lat: Double, val lon: Double)

class QueryObject {

    @DummyDirective
    fun query(value: Int): Geography = Geography(value, GeoType.CITY, listOf())
}

class QueryWithDeprecatedFields {
    @Deprecated("this query is deprecated")
    fun deprecatedQuery(something: String) = something

    @Deprecated("this query is also deprecated", replaceWith = ReplaceWith("shinyNewQuery"))
    fun deprecatedQueryWithReplacement(something: String) = something

    fun deprecatedFieldQuery(something: String) = ClassWithDeprecatedField(something, something.reversed())

    fun deprecatedArgumentQuery(input: ClassWithDeprecatedField) = input.something
}

data class ClassWithDeprecatedField(
    val something: String,
    @Deprecated("this field is deprecated")
    val deprecatedField: String
)
