import com.android.build.api.dsl.LibraryExtension
import io.github.hlcaptain.symbols.gradle.SymbolFontsExtension
import io.github.hlcaptain.symbols.gradle.SymbolFontsPlugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MaterialFontLibraryPlugin : ComposeMultiplatformLibraryPlugin() {
    override fun apply(target: Project) = with(target) {
        val convention = materialFontConvention(name)

        super.apply(target)
        pluginManager.apply(KmpPublishingPlugin::class.java)
        pluginManager.apply(SymbolFontsPlugin::class.java)

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                api(dependencies.project(mapOf("path" to ":modules:material-compose")))
                implementation(defaultLibs.findLibrary("compose-resources").get())
            }
        }
        extensions.configure<LibraryExtension> {
            namespace = convention.namespace
        }
        extensions
            .getByType<ComposeExtension>()
            .extensions
            .getByType<ResourcesExtension>()
            .packageOfResClass = convention.resourcePackage
        extensions.configure<SymbolFontsExtension> {
            composeFontResources.from(
                rootProject.layout.projectDirectory.dir(
                    "fonts/material/${convention.resourceDirectory}/composeResources",
                ),
            )
            fontAccessor(convention.resourceAccessor) { accessor ->
                accessor.packageName.set(MaterialPackage)
                accessor.receiver.set(convention.receiver)
                accessor.propertyName.set(convention.propertyName)
                accessor.componentIndex.set(convention.componentIndex)
                accessor.fixedAxisValues.putAll(convention.fixedAxisValues)
            }
        }
    }
}

/** Derived configuration for one of the six built-in Material font modules. */
internal data class MaterialFontConvention(
    val style: String,
    val isStatic: Boolean,
) {
    val resourceDirectory: String = style + if (isStatic) "-static" else ""
    val namespace: String = "$MaterialPackage.$style" + if (isStatic) ".staticfont" else ""
    val resourcePackage: String = "$namespace.resources"
    val resourceAccessor: String =
        "material_symbols_${style}_${if (isStatic) "regular" else "variable"}"
    val receiver: String = "$MaterialPackage.Icons.${style.replaceFirstChar(Char::uppercaseChar)}"
    val propertyName: String = if (isStatic) "staticFont" else "font"
    val componentIndex: Int = if (isStatic) 2 else 1
    val fixedAxisValues: Map<String, Float> = if (isStatic) MaterialDefaultAxes else emptyMap()
}

/** Returns the convention for an exact built-in Material font project name. */
internal fun materialFontConvention(projectName: String): MaterialFontConvention =
    when (projectName) {
        "material-outlined" -> MaterialFontConvention("outlined", isStatic = false)
        "material-outlined-static" -> MaterialFontConvention("outlined", isStatic = true)
        "material-rounded" -> MaterialFontConvention("rounded", isStatic = false)
        "material-rounded-static" -> MaterialFontConvention("rounded", isStatic = true)
        "material-sharp" -> MaterialFontConvention("sharp", isStatic = false)
        "material-sharp-static" -> MaterialFontConvention("sharp", isStatic = true)
        else -> error("Unsupported Material font project: $projectName")
    }

private const val MaterialPackage = "io.github.hlcaptain.symbols.material"

private val MaterialDefaultAxes: Map<String, Float> = mapOf(
    "FILL" to 0f,
    "GRAD" to 0f,
    "opsz" to 24f,
    "wght" to 400f,
)
