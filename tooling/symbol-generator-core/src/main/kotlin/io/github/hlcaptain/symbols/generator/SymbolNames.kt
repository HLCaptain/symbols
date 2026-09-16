package io.github.hlcaptain.symbols.generator

/** Shared naming and validation rules for manifests and generated APIs. */
object SymbolNames {
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

    /**
     * Checks whether [value] can be used as a manifest symbol name.
     *
     * An accepted manifest name contains lowercase ASCII letters or digits in
     * segments separated by single underscores, for example `arrow_back` or
     * `3d_rotation`.
     * This check has no side effects and does not throw for an invalid value.
     *
     * @param value name to check.
     * @return `true` when [value] follows the accepted manifest format.
     */
    fun isCanonicalName(value: String): Boolean =
        canonicalName.matches(value)

    /**
     * Converts an accepted manifest name into a Kotlin property name that does
     * not need backticks.
     *
     * Underscore-separated parts are capitalized and joined. A name beginning
     * with a digit receives a leading underscore, so `3d_rotation` becomes
     * `_3dRotation`. The input string is not changed.
     *
     * @param canonicalName lowercase manifest name to convert.
     * @return Kotlin identifier used by generated symbol accessors.
     * @throws IllegalArgumentException if [canonicalName] does not follow the
     * accepted manifest format.
     */
    fun kotlinIdentifier(canonicalName: String): String {
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

    /**
     * Converts a Kotlin type identifier into a lowercase package segment.
     *
     * A lower-to-upper case boundary becomes an underscore. If the result is a
     * Kotlin keyword, it receives a leading underscore. This function only
     * returns the normalized text and does not create a package or directory.
     *
     * @param typeIdentifier valid unescaped Kotlin type identifier to convert.
     * @return a package-safe lowercase segment.
     * @throws IllegalArgumentException if [typeIdentifier] is not a valid
     * unescaped Kotlin identifier.
     */
    fun packageSegment(typeIdentifier: String): String {
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

    /**
     * Verifies that [value] is a dot-separated Kotlin package name whose
     * segments do not need backticks.
     *
     * This function returns normally for a valid package and changes no state.
     *
     * @param value package name to validate.
     * @throws IllegalArgumentException if the package is malformed, contains a
     * Kotlin keyword, or contains an underscore-only segment.
     */
    fun requirePackageName(value: String) {
        val segments = value.split('.')
        require(
            packageName.matches(value) &&
                segments.all(::isUnescapedKotlinIdentifier),
        ) {
            "Invalid Kotlin package name: $value"
        }
    }

    /**
     * Verifies that [value] is a Kotlin identifier that can be emitted without
     * backticks.
     *
     * [label] is used only to make a failure message identify the setting being
     * checked. This function returns normally and changes no state when valid.
     *
     * @param value identifier to validate.
     * @param label plain-language setting name used in an error message.
     * @throws IllegalArgumentException if [value] is malformed, is a Kotlin
     * keyword, or contains only underscores.
     */
    fun requireTypeIdentifier(value: String, label: String) {
        require(
            typeIdentifier.matches(value) &&
                !value.first().isDigit() &&
                isUnescapedKotlinIdentifier(value),
        ) {
            "Invalid $label: $value"
        }
    }

    /**
     * Derives a lowercase Android resource prefix from [value].
     *
     * Letters and digits recognized by Kotlin are retained, lower-to-upper case
     * boundaries become underscores, and runs of other characters collapse to
     * one underscore. A result beginning with a digit receives the `symbols_`
     * prefix. Use ASCII input when the result will be passed to
     * [AndroidVectorXmlRenderer], whose resource names follow Android's lowercase
     * ASCII rules. This function returns text only and does not create or
     * validate an Android resource.
     *
     * @param value icon-set or style name to normalize.
     * @return a nonempty normalized prefix candidate.
     * @throws IllegalArgumentException if [value] contains no letters or digits.
     */
    fun androidResourcePrefix(value: String): String {
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
