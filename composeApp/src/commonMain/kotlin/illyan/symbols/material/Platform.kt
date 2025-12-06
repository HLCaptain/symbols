package illyan.symbols.material

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform