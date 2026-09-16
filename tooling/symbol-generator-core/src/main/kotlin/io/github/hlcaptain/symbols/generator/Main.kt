package io.github.hlcaptain.symbols.generator

import java.io.PrintStream
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Runs the command-line symbol generator and ends the current process with its
 * result code.
 *
 * Use [SymbolGeneratorCli.run] instead when calling the generator from another
 * JVM process, such as a Gradle worker or a test, because that function returns
 * the result code without ending the process.
 *
 * @param arguments command-line options described by `--help`.
 */
fun main(arguments: Array<String>) {
    exitProcess(SymbolGeneratorCli.run(arguments))
}

/** Small deterministic CLI intended for Gradle Worker and maintainer use. */
object SymbolGeneratorCli {
    /**
     * Generates Kotlin vectors, Android vector drawables, or Compose drawable
     * resources from the supplied command-line options.
     *
     * Font and SVG inputs are read from disk. Generated files are written to
     * the requested output directories, and stale files recorded by an earlier
     * run are removed. The supplied streams are written to but are not closed.
     * Unlike [main], this function does not end the current process.
     *
     * @param arguments command-line options described by `--help`.
     * @param standardOut receives help text and a successful generation summary.
     * @param standardError receives validation and generation errors.
     * @return `0` for help or successful generation, `2` for invalid options or
     * rejected input, and `1` for another generation failure.
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
            val options = CliOptions.parse(arguments)
            generate(options, standardOut)
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

    private fun generate(options: CliOptions, standardOut: PrintStream) {
        val renderOptions = VectorRenderOptions(
            viewportWidth = options.viewportWidth,
            viewportHeight = options.viewportHeight,
            defaultWidthDp = options.defaultWidthDp,
            defaultHeightDp = options.defaultHeightDp,
            precision = options.precision,
            symbolsPerFile = options.symbolsPerFile,
            autoMirror = options.autoMirror,
            fillColor = options.fillColor,
        )
        val generator = SymbolGenerator()
        val rendered = if (options.svgDirectory != null) {
            val icons = generator.extract(
                SvgExtractionRequest(
                    svgDirectory = options.svgDirectory,
                    viewportWidth = options.viewportWidth,
                    viewportHeight = options.viewportHeight,
                    conicTolerance = options.conicTolerance,
                ),
            )
            val iconSet = GeneratedSvgIconSet(
                packageName = options.packageName,
                name = options.setName,
                styles = listOf(GeneratedSvgStyle(options.styleName, icons)),
            )
            GenerationResult(
                imageVectors = options.kotlinOutput?.let {
                    generator.imageVectors(
                        iconSet,
                        renderOptions,
                        includeNamespace = !options.omitKotlinNamespace,
                    )
                },
                nativeVectors = if (
                    options.androidOutput != null ||
                    options.composeOutput != null
                ) {
                    generator.androidVectors(
                        iconSet,
                        options.resourcePrefix,
                        renderOptions,
                    ).files
                } else {
                    null
                },
                summary = "Generated ${icons.size} SVG icons",
            )
        } else {
            val manifest = requireNotNull(options.manifest)
            val font = requireNotNull(options.font)
            val catalog = SymbolManifestParser.parse(manifest)
            val extracted = generator.extract(
                catalog = catalog,
                request = FontExtractionRequest(
                    fontFile = font,
                    codePoints = catalog.uniqueCodePoints,
                    fontIndex = options.fontIndex,
                    axes = options.axes,
                    transform = OutlineTransform(
                        viewportWidth = options.viewportWidth,
                        viewportHeight = options.viewportHeight,
                        emSize = options.emSize,
                        originX = options.originX,
                        baselineY = options.baselineY,
                    ),
                    conicTolerance = options.conicTolerance,
                ),
            )
            val iconSet = GeneratedIconSet(
                packageName = options.packageName,
                name = options.setName,
                styles = listOf(
                    GeneratedStyle(
                        name = options.styleName,
                        catalog = catalog,
                        font = extracted,
                    ),
                ),
            )
            GenerationResult(
                imageVectors = options.kotlinOutput?.let {
                    generator.imageVectors(
                        iconSet,
                        renderOptions,
                        includeNamespace = !options.omitKotlinNamespace,
                    )
                },
                nativeVectors = if (
                    options.androidOutput != null ||
                    options.composeOutput != null
                ) {
                    generator.androidVectors(
                        iconSet,
                        options.resourcePrefix,
                        renderOptions,
                    ).files
                } else {
                    null
                },
                summary =
                    "Generated ${catalog.entries.size} names / " +
                        "${catalog.uniqueCodePoints.size} code points from " +
                        extracted.familyName,
            )
        }
        val summaries = buildList {
            options.kotlinOutput?.let { output ->
                val result = GeneratedFileWriter.synchronize(
                    output,
                    requireNotNull(rendered.imageVectors),
                )
                add("Kotlin: $result")
            }
            options.androidOutput?.let { output ->
                val result = GeneratedFileWriter.synchronize(
                    output,
                    requireNotNull(rendered.nativeVectors),
                )
                add("Android XML: $result")
            }
            options.composeOutput?.let { output ->
                val result = GeneratedFileWriter.synchronize(
                    output,
                    requireNotNull(rendered.nativeVectors),
                )
                add("Compose XML: $result")
            }
        }
        standardOut.println("${rendered.summary}; ${summaries.joinToString()}")
    }

    private fun usage(): String =
        """
        |Usage: symbol-generator-core [options]
        |
        |Required inputs:
        |  --package PACKAGE           Generated Kotlin base package
        |  --set NAME                  Generated root object, for example AppIcons
        |  --style NAME                Generated style object, for example Rounded
        |
        |Source (exactly one mode; every icon is generated):
        |  --svg-directory DIR         Flat directory of path-based monochrome SVGs
        |  --font PATH                 TTF, OTF, or TTC input; requires --manifest
        |  --manifest PATH             <snake_case_name> <hex_code_point> manifest
        |
        |Outputs (at least one, directories must not overlap):
        |  --kotlin-output DIR         ImageVector source output directory
        |  --android-output DIR        Android res output directory
        |  --compose-output DIR        Compose resources output directory
        |
        |Other options:
        |  --font-index N              TTC face index (default 0)
        |  --axis TAG=VALUE            Variable coordinate; repeat as needed
        |  --viewport-width N          Viewport width (default 24)
        |  --viewport-height N         Viewport height (default 24)
        |  --default-width-dp N        Default width (default viewport width)
        |  --default-height-dp N       Default height (default viewport height)
        |  --em-size N                 Viewport units occupied by one em
        |  --origin-x N                X origin (default 0)
        |  --baseline-y N              Baseline (default viewport height)
        |  --precision N               Decimal places, 0..8 (default 4)
        |  --symbols-per-file N        Independent builders per file (default 64)
        |  --conic-tolerance N         Conic approximation tolerance (default .002)
        |  --fill-color #AARRGGBB      Monochrome fill (default #FF000000)
        |  --resource-prefix NAME      Android resource prefix (default from set)
        |  --auto-mirror               Mark generated vectors auto-mirrored
        |  --omit-kotlin-namespace     Let a build integration own the root object
        |  --help                      Show this message
        |""".trimMargin()
}

private data class GenerationResult(
    val imageVectors: RenderedFiles?,
    val nativeVectors: RenderedFiles?,
    val summary: String,
)

private data class CliOptions(
    val font: Path?,
    val manifest: Path?,
    val svgDirectory: Path?,
    val packageName: String,
    val setName: String,
    val styleName: String,
    val kotlinOutput: Path?,
    val androidOutput: Path?,
    val composeOutput: Path?,
    val fontIndex: Int,
    val axes: Map<String, Float>,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val defaultWidthDp: Float,
    val defaultHeightDp: Float,
    val emSize: Float,
    val originX: Float,
    val baselineY: Float,
    val precision: Int,
    val symbolsPerFile: Int,
    val conicTolerance: Float,
    val autoMirror: Boolean,
    val omitKotlinNamespace: Boolean,
    val fillColor: String,
    val resourcePrefix: String,
) {
    companion object {
        fun parse(arguments: Array<String>): CliOptions {
            val values = linkedMapOf<String, MutableList<String>>()
            var autoMirror = false
            var omitKotlinNamespace = false
            var index = 0
            while (index < arguments.size) {
                val option = arguments[index]
                if (
                    option == "--auto-mirror" ||
                    option == "--omit-kotlin-namespace"
                ) {
                    when (option) {
                        "--auto-mirror" -> {
                            require(!autoMirror) {
                                "--auto-mirror may be supplied only once"
                            }
                            autoMirror = true
                        }
                        else -> {
                            require(!omitKotlinNamespace) {
                                "--omit-kotlin-namespace may be supplied only once"
                            }
                            omitKotlinNamespace = true
                        }
                    }
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

            val known = setOf(
                "--font",
                "--manifest",
                "--svg-directory",
                "--package",
                "--set",
                "--style",
                "--kotlin-output",
                "--android-output",
                "--compose-output",
                "--font-index",
                "--axis",
                "--viewport-width",
                "--viewport-height",
                "--default-width-dp",
                "--default-height-dp",
                "--em-size",
                "--origin-x",
                "--baseline-y",
                "--precision",
                "--symbols-per-file",
                "--conic-tolerance",
                "--fill-color",
                "--resource-prefix",
            )
            val unknown = values.keys - known
            require(unknown.isEmpty()) {
                "Unknown options: ${unknown.sorted().joinToString()}"
            }

            fun required(name: String): String =
                values[name]?.singleOrNull()
                    ?: throw IllegalArgumentException("Exactly one $name is required")

            fun optional(name: String): String? {
                val candidates = values[name].orEmpty()
                require(candidates.size <= 1) { "$name may be supplied only once" }
                return candidates.singleOrNull()
            }

            val packageName = required("--package")
            val setName = required("--set")
            val styleName = required("--style")
            SymbolNames.requirePackageName(packageName)
            SymbolNames.requireTypeIdentifier(setName, "icon-set name")
            SymbolNames.requireTypeIdentifier(styleName, "style name")

            val kotlinOutput = optional("--kotlin-output")?.let(Path::of)
            val androidOutput = optional("--android-output")?.let(Path::of)
            val composeOutput = optional("--compose-output")?.let(Path::of)
            require(
                kotlinOutput != null ||
                    androidOutput != null ||
                    composeOutput != null,
            ) {
                "At least one output directory is required"
            }
            val normalizedOutputs = listOfNotNull(
                kotlinOutput,
                androidOutput,
                composeOutput,
            ).map { output -> output.toAbsolutePath().normalize() }
            require(
                normalizedOutputs.indices.none { first ->
                    normalizedOutputs.indices.any { second ->
                        first != second &&
                            normalizedOutputs[first].startsWith(normalizedOutputs[second])
                    }
                },
            ) {
                "Output directories must be distinct and non-overlapping"
            }

            val font = optional("--font")?.let(Path::of)
            val manifest = optional("--manifest")?.let(Path::of)
            val svgDirectory = optional("--svg-directory")?.let(Path::of)
            require(
                (svgDirectory != null && font == null && manifest == null) ||
                    (svgDirectory == null && font != null && manifest != null),
            ) {
                "Use either --svg-directory, or both --font and --manifest"
            }
            if (svgDirectory != null) {
                val unsupported = FontOnlyOptions.filter(values::containsKey)
                require(unsupported.isEmpty()) {
                    "SVG sources do not support font-only options: " +
                        unsupported.joinToString()
                }
            }

            fun floatValue(name: String, default: Float): Float {
                val rawValue = optional(name) ?: return default
                return rawValue.toFloatOrNull()
                    ?: throw IllegalArgumentException(
                        "$name must be a number, but was '$rawValue'",
                    )
            }

            fun intValue(name: String, default: Int): Int {
                val rawValue = optional(name) ?: return default
                return rawValue.toIntOrNull()
                    ?: throw IllegalArgumentException(
                        "$name must be an integer, but was '$rawValue'",
                    )
            }

            val viewportWidth = floatValue("--viewport-width", 24f)
            val viewportHeight = floatValue("--viewport-height", 24f)
            val axes = values["--axis"].orEmpty()
                .associate { argument ->
                    val separator = argument.indexOf('=')
                    require(separator == 4 && separator < argument.lastIndex) {
                        "Axes must have the form TAG=VALUE: $argument"
                    }
                    val tag = argument.substring(0, separator)
                    val value = argument.substring(separator + 1).toFloatOrNull()
                        ?: throw IllegalArgumentException(
                            "Invalid axis value: $argument",
                        )
                    tag to value
                }
            require(axes.size == values["--axis"].orEmpty().size) {
                "Each axis tag may be supplied only once"
            }

            return CliOptions(
                font = font,
                manifest = manifest,
                svgDirectory = svgDirectory,
                packageName = packageName,
                setName = setName,
                styleName = styleName,
                kotlinOutput = kotlinOutput,
                androidOutput = androidOutput,
                composeOutput = composeOutput,
                fontIndex = intValue("--font-index", 0),
                axes = axes,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                defaultWidthDp =
                    floatValue("--default-width-dp", viewportWidth),
                defaultHeightDp =
                    floatValue("--default-height-dp", viewportHeight),
                emSize =
                    floatValue(
                        "--em-size",
                        minOf(viewportWidth, viewportHeight),
                    ),
                originX = floatValue("--origin-x", 0f),
                baselineY =
                    floatValue("--baseline-y", viewportHeight),
                precision = intValue("--precision", 4),
                symbolsPerFile =
                    intValue("--symbols-per-file", 64),
                conicTolerance =
                    floatValue("--conic-tolerance", 0.002f),
                autoMirror = autoMirror,
                omitKotlinNamespace = omitKotlinNamespace,
                fillColor = optional("--fill-color") ?: "#FF000000",
                resourcePrefix =
                    optional("--resource-prefix")
                        ?: SymbolNames.androidResourcePrefix(setName),
            )
        }
    }
}

private val FontOnlyOptions: Set<String> = setOf(
    "--font-index",
    "--axis",
    "--em-size",
    "--origin-x",
    "--baseline-y",
)
