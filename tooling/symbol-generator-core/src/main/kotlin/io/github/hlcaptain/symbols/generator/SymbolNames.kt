package io.github.hlcaptain.symbols.generator

/** Shared naming and validation rules for manifests and generated APIs. */
public object SymbolNames {
    private val canonicalName =
        Regex("^[a-z0-9]+(?:_[a-z0-9]+)*$")
    private val packageName =
        Regex("^[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*$")
    private val typeIdentifier =
        Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val kotlinHardKeywords = setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
    )

    public fun isCanonicalName(value: String): Boolean =
        canonicalName.matches(value)

    public fun kotlinIdentifier(canonicalName: String): String {
        require(isCanonicalName(canonicalName)) {
            "Invalid canonical name: $canonicalName"
        }
        val identifier = canonicalName
            .split('_')
            .joinToString(separator = "") { part ->
                part.replaceFirstChar(Char::uppercaseChar)
            }
        return if (identifier.first().isDigit()) "_$identifier" else identifier
    }

    public fun packageSegment(typeIdentifier: String): String {
        requireTypeIdentifier(typeIdentifier, "type identifier")
        val normalized = buildString(typeIdentifier.length + 4) {
            typeIdentifier.forEachIndexed { index, character ->
                if (
                    character.isUpperCase() &&
                    index > 0 &&
                    typeIdentifier[index - 1].isLowerCase()
                ) {
                    append('_')
                }
                append(character.lowercaseChar())
            }
        }
        return if (normalized in kotlinHardKeywords) "_$normalized" else normalized
    }

    internal fun requireDistinctStylePackageSegments(styleNames: Collection<String>) {
        val collisions = styleNames
            .groupBy(::packageSegment)
            .filterValues { names -> names.distinct().size > 1 }
        if (collisions.isNotEmpty()) {
            val details = collisions.entries
                .sortedBy(Map.Entry<String, List<String>>::key)
                .joinToString("; ") { (segment, names) ->
                    "$segment <- ${names.distinct().sorted().joinToString()}"
                }
            throw SymbolGenerationException(
                "Generated style package-segment collisions: $details",
            )
        }
    }

    internal fun requireDistinctStyleAndroidResourcePrefixes(
        styleNames: Collection<String>,
    ) {
        val collisions = styleNames
            .groupBy(::androidResourcePrefix)
            .filterValues { names -> names.distinct().size > 1 }
        if (collisions.isNotEmpty()) {
            val details = collisions.entries
                .sortedBy(Map.Entry<String, List<String>>::key)
                .joinToString("; ") { (prefix, names) ->
                    "$prefix <- ${names.distinct().sorted().joinToString()}"
                }
            throw SymbolGenerationException(
                "Generated style Android-resource-prefix collisions: $details",
            )
        }
    }

    public fun requirePackageName(value: String) {
        val segments = value.split('.')
        require(
            packageName.matches(value) &&
                segments.all(::isUnescapedKotlinIdentifier),
        ) {
            "Invalid Kotlin package name: $value"
        }
    }

    public fun requireTypeIdentifier(value: String, label: String) {
        require(
            typeIdentifier.matches(value) &&
                !value.first().isDigit() &&
                isUnescapedKotlinIdentifier(value),
        ) {
            "Invalid $label: $value"
        }
    }

    public fun androidResourcePrefix(value: String): String {
        val normalized = buildString(value.length + 8) {
            value.forEachIndexed { index, character ->
                when {
                    character.isLetterOrDigit() -> {
                        if (
                            character.isUpperCase() &&
                            index > 0 &&
                            lastOrNull()?.isLetterOrDigit() == true &&
                            last().isLowerCase()
                        ) {
                            append('_')
                        }
                        append(character.lowercaseChar())
                    }
                    lastOrNull() != '_' -> append('_')
                }
            }
        }.trim('_')
        require(normalized.isNotEmpty()) {
            "Cannot derive an Android resource prefix from '$value'"
        }
        return if (normalized.first().isDigit()) "symbols_$normalized" else normalized
    }

    private fun isUnescapedKotlinIdentifier(value: String): Boolean =
        value !in kotlinHardKeywords && value.any { character -> character != '_' }
}
