package io.github.hlcaptain.symbols.generator

import java.io.PrintStream
import java.nio.file.Path
import kotlin.system.exitProcess

/** Compile-time generator for typed Compose symbol-font resource descriptors. */
object SymbolFontDescriptorsCli {
    /**
     * Runs the font-descriptor command and ends the current process with its
     * result code.
     *
     * Use [run] when invoking this command from an existing JVM process because
     * it returns the result code instead of ending that process.
     *
     * @param arguments command-line options described by `--help`.
     */
    @JvmStatic
    fun main(arguments: Array<String>) {
        exitProcess(run(arguments))
    }

    /**
     * Scans Compose font resources and writes typed descriptors for the fonts
     * found directly in each configured `font` directory.
     *
     * Resource directories and font files are read from disk. Generated Kotlin
     * is synchronized with the output directory, so previously recorded files
     * that are no longer produced are deleted. The supplied streams are written
     * to but are not closed. This function does not end the current process.
     *
     * @param arguments command-line options described by `--help`.
     * @param standardOut receives help text and a successful generation summary.
     * @param standardError receives validation and generation errors.
     * @return `0` for help or successful generation, `2` for invalid options or
     * rejected input, and `1` for another scanning or writing failure.
     */
    fun run(
        arguments: Array<String>,
        standardOut: PrintStream = System.out,
        standardError: PrintStream = System.err,
    ): Int {
        if (arguments.any { it == "--help" || it == "-h" }) {
            standardOut.print(usage())
            return 0
        }

        return try {
            val options = FontDescriptorCliOptions.parse(arguments)
            val descriptors = SkikoFontDescriptorScanner().scan(options.resourceRoots)
            val result = GeneratedFileWriter.synchronize(
                outputDirectory = options.output,
                renderedFiles = KotlinFontDescriptorsRenderer().render(
                    packageName = options.packageName,
                    resClassName = options.resClassName,
                    publicAccessors = options.publicAccessors,
                    descriptors = descriptors,
                ),
            )
            standardOut.println("Generated ${descriptors.size} font descriptors; $result")
            0
        } catch (error: IllegalArgumentException) {
            standardError.println("error: ${error.message}")
            standardError.println("Run with --help for usage.")
            2
        } catch (error: Exception) {
            standardError.println("error: ${error.message ?: error::class.simpleName}")
            1
        }
    }

    private fun usage(): String =
        """
        |Usage: symbol-font-descriptors [options]
        |
        |Required:
        |  --resource-root DIR         Compose resource root containing font/; repeatable
        |  --package PACKAGE           Package containing the generated Compose Res class
        |  --res-class NAME            Generated Compose resource class name
        |  --output DIR                Generated Kotlin output directory
        |
        |Optional:
        |  --public                    Emit public accessors instead of internal accessors
        |  --help                      Show this message
        |""".trimMargin()
}

private data class FontDescriptorCliOptions(
    val resourceRoots: List<Path>,
    val packageName: String,
    val resClassName: String,
    val output: Path,
    val publicAccessors: Boolean,
) {
    companion object {
        fun parse(arguments: Array<String>): FontDescriptorCliOptions {
            val values = linkedMapOf<String, MutableList<String>>()
            var publicAccessors = false
            var index = 0
            while (index < arguments.size) {
                val option = arguments[index]
                if (option == "--public") {
                    require(!publicAccessors) { "--public may be supplied only once" }
                    publicAccessors = true
                    index += 1
                    continue
                }
                require(option.startsWith("--")) { "Unexpected argument: $option" }
                require(index + 1 < arguments.size) { "Missing value for $option" }
                val value = arguments[index + 1]
                require(!value.startsWith("--")) { "Missing value for $option" }
                values.getOrPut(option) { mutableListOf() }.add(value)
                index += 2
            }

            val known = setOf("--resource-root", "--package", "--res-class", "--output")
            val unknown = values.keys - known
            require(unknown.isEmpty()) {
                "Unknown options: ${unknown.sorted().joinToString()}"
            }

            fun required(name: String): String =
                values[name]?.singleOrNull()
                    ?: throw IllegalArgumentException("Exactly one $name is required")

            val roots = values["--resource-root"].orEmpty()
            require(roots.isNotEmpty()) { "At least one --resource-root is required" }
            val packageName = required("--package")
            val resClassName = required("--res-class")
            SymbolNames.requirePackageName(packageName)
            SymbolNames.requireTypeIdentifier(resClassName, "Compose resource class name")
            return FontDescriptorCliOptions(
                resourceRoots = roots.map(Path::of),
                packageName = packageName,
                resClassName = resClassName,
                output = Path.of(required("--output")),
                publicAccessors = publicAccessors,
            )
        }
    }
}
