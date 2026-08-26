package io.github.hlcaptain.symbols.sample.imagevectormigration

import io.github.hlcaptain.symbols.Symbols
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.Academmunicons
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.Tabler
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.default.Orcid
import io.github.hlcaptain.symbols.sample.imagevectormigration.generated.outline.Home
import kotlin.test.Test
import kotlin.test.assertSame

class GeneratedSymbolsNamespaceTest {
    @Test
    fun commonRootReusesGeneratedSetStyleAndIconSingletons() {
        assertSame(Academmunicons, Symbols.Academmunicons)
        assertSame(Academmunicons.Default, Symbols.Academmunicons.Default)
        assertSame(Academmunicons.Default.Orcid, Symbols.Academmunicons.Default.Orcid)

        assertSame(Tabler, Symbols.Tabler)
        assertSame(Tabler.Outline, Symbols.Tabler.Outline)
        assertSame(Tabler.Outline.Home, Symbols.Tabler.Outline.Home)
    }
}
