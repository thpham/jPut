package cz.encircled.jput.context

inline fun <reified T> getProperty(key: String, defaultValue: T? = null): T {
    val value = context.propertySources
            .mapNotNull { it.getProperty(key) }
            .firstOrNull()

    if (value.isNullOrBlank()) return defaultValue ?: throw IllegalStateException("JPut property [$key] is mandatory")

    return when (T::class) {
        Boolean::class -> value.toBoolean() as T
        Int::class -> value.toInt() as T
        else -> value as T
    }
}

interface PropertySource {

    fun getProperty(key: String): String?

}

class SystemPropertySource : PropertySource {

    override fun getProperty(key: String): String? = System.getProperty(key)

}