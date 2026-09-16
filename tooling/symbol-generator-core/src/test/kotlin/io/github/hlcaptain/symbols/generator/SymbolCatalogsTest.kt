package io.github.hlcaptain.symbols.generator

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SymbolCatalogsTest {
    @Test
    fun rendersSortedNamedCatalogsInBoundedChunks() {
        val rendered = KotlinSymbolCatalogsRenderer(entriesPerChunk = 2).render(
            packageName = "com.example.catalogs",
            catalogs = linkedMapOf(
                "SecondCatalog" to SymbolCatalog.of(
                    listOf(SymbolEntry("zeta", 0xF002)),
                ),
                "FirstCatalog" to SymbolCatalog.of(
                    listOf(
                        SymbolEntry("gamma", 0x1F600),
                        SymbolEntry("alpha", 0xF001),
                        SymbolEntry("beta", 0xF000),
                    ),
                ),
            ),
        )

        val source = rendered.files.getValue(
            "com/example/catalogs/SymbolCatalogs.generated.kt",
        )
        assertTrue("internal data class SymbolCatalogEntry" in source)
        assertTrue("internal val FirstCatalog: List<SymbolCatalogEntry>" in source)
        assertTrue("internal val SecondCatalog: List<SymbolCatalogEntry>" in source)
        assertTrue(source.indexOf("FirstCatalog") < source.indexOf("SecondCatalog"))
        assertTrue(source.indexOf("\"alpha\"") < source.indexOf("\"beta\""))
        assertTrue(source.indexOf("\"beta\"") < source.indexOf("\"gamma\""))
        assertTrue("SymbolCatalogEntry(\"gamma\", 0x1F600)" in source)
        assertTrue("symbolCatalogChunk000()" in source)
        assertTrue("symbolCatalogChunk001()" in source)
        assertTrue("symbolCatalogChunk002()" in source)
        assertFalse("DemoIcon" in source)
    }

    @Test
    fun rejectsCatalogNamesThatConflictWithGeneratedDeclarations() {
        val catalog = SymbolCatalog.of(listOf(SymbolEntry("home", 0xE001)))

        assertFailsWith<IllegalArgumentException> {
            KotlinSymbolCatalogsRenderer().render(
                packageName = "com.example.catalogs",
                catalogs = mapOf("SymbolCatalogEntry" to catalog),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            KotlinSymbolCatalogsRenderer().render(
                packageName = "com.example.catalogs",
                catalogs = mapOf("bad-name" to catalog),
            )
        }
    }

    @Test
    fun rejectsCatalogNamesWithCollidingJvmGetters() {
        val catalog = SymbolCatalog.of(listOf(SymbolEntry("home", 0xE001)))

        val failure = assertFailsWith<IllegalArgumentException> {
            KotlinSymbolCatalogsRenderer().render(
                packageName = "com.example.catalogs",
                catalogs = mapOf(
                    "Catalog" to catalog,
                    "catalog" to catalog,
                ),
            )
        }

        assertTrue(
            "getCatalog <- Catalog, catalog" in failure.message.orEmpty(),
        )
    }
}
