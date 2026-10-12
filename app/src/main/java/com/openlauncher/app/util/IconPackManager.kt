package com.openlauncher.app.util

import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import com.openlauncher.app.model.IconPackInfo
import org.xmlpull.v1.XmlPullParser

/** Reads the de-facto appfilter.xml format used by ADW, Nova, Lawnchair and similar packs. */
object IconPackManager {

    fun discover(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { application ->
                runCatching {
                    val resources = pm.getResourcesForApplication(application)
                    hasAppFilter(resources, application.packageName)
                }.getOrDefault(false)
            }
            .mapNotNull { application ->
                runCatching {
                    IconPackInfo(
                        packageName = application.packageName,
                        displayName = pm.getApplicationLabel(application).toString(),
                        icon = pm.getApplicationIcon(application)
                    )
                }.getOrNull()
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    fun resolver(context: Context, iconPackPackage: String): Resolver? {
        if (iconPackPackage.isBlank()) return null
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconPackPackage)
            val entries = readAppFilter(resources, iconPackPackage)
            if (entries.byComponent.isEmpty() && entries.byPackage.isEmpty()) null
            else Resolver(resources, iconPackPackage, entries)
        }.getOrNull()
    }

    fun listDrawableNames(context: Context, iconPackPackage: String): List<String> {
        if (iconPackPackage.isBlank()) return emptyList()
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconPackPackage)
            buildSet {
                val entries = readAppFilter(resources, iconPackPackage)
                addAll(entries.byComponent.values.map(::cleanDrawableName))
                addAll(readDrawableCatalog(resources, iconPackPackage))
            }
                .filter(String::isNotBlank)
                .sorted()
        }.getOrDefault(emptyList())
    }

    fun loadDrawable(context: Context, iconPackPackage: String, drawableName: String): Drawable? {
        if (iconPackPackage.isBlank() || drawableName.isBlank()) return null
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconPackPackage)
            loadDrawable(resources, iconPackPackage, drawableName)
        }.getOrNull()
    }

    class Resolver internal constructor(
        private val resources: Resources,
        private val iconPackPackage: String,
        private val entries: Entries
    ) {
        fun iconFor(packageName: String, componentName: ComponentName?): Drawable? {
            val drawableName = componentName?.let {
                entries.byComponent[it.flattenToString()]
                    ?: entries.byComponent[it.flattenToShortString()]
            } ?: entries.byPackage[packageName]
            return drawableName?.let(::loadDrawable)
        }

        private fun loadDrawable(name: String): Drawable? {
            return IconPackManager.loadDrawable(resources, iconPackPackage, name)
        }
    }

    internal data class Entries(
        val byComponent: Map<String, String>,
        val byPackage: Map<String, String>
    )

    private fun hasAppFilter(resources: Resources, packageName: String): Boolean {
        if (resources.getIdentifier("appfilter", "xml", packageName) != 0) return true
        return runCatching {
            resources.assets.open("appfilter.xml").use { true }
        }.getOrDefault(false)
    }

    private fun readAppFilter(resources: Resources, packageName: String): Entries {
        val resourceId = resources.getIdentifier("appfilter", "xml", packageName)
        return if (resourceId != 0) {
            resources.getXml(resourceId).use(::parseAppFilter)
        } else {
            resources.assets.open("appfilter.xml").use { input ->
                val parser = Xml.newPullParser().apply {
                    setInput(input, null)
                }
                parseAppFilter(parser)
            }
        }
    }

    private fun readDrawableCatalog(resources: Resources, packageName: String): Set<String> {
        val names = linkedSetOf<String>()
        val resourceId = resources.getIdentifier("drawable", "xml", packageName)
        if (resourceId != 0) {
            runCatching {
                resources.getXml(resourceId).use { parser -> collectDrawableNames(parser, names) }
            }
        }
        runCatching {
            resources.assets.open("drawable.xml").use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, null) }
                collectDrawableNames(parser, names)
            }
        }
        return names
    }

    private fun collectDrawableNames(parser: XmlPullParser, output: MutableSet<String>) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                parser.getAttributeValue(null, "drawable")
                    ?.let(::cleanDrawableName)
                    ?.takeIf(String::isNotBlank)
                    ?.let(output::add)
            }
            event = parser.next()
        }
    }

    private fun cleanDrawableName(name: String): String =
        name.substringAfterLast('/').removePrefix("@").trim()

    private fun loadDrawable(
        resources: Resources,
        iconPackPackage: String,
        drawableName: String
    ): Drawable? {
        val cleanName = cleanDrawableName(drawableName)
        val drawableId = resources.getIdentifier(cleanName, "drawable", iconPackPackage)
            .takeIf { it != 0 }
            ?: resources.getIdentifier(cleanName, "mipmap", iconPackPackage).takeIf { it != 0 }
            ?: return null
        return ResourcesCompat.getDrawable(resources, drawableId, null)
    }

    private fun parseAppFilter(parser: XmlPullParser): Entries {
        val byComponent = linkedMapOf<String, String>()
        val byPackage = linkedMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                val componentValue = parser.getAttributeValue(null, "component").orEmpty()
                val drawableValue = parser.getAttributeValue(null, "drawable").orEmpty()
                if (componentValue.isNotBlank() && drawableValue.isNotBlank()) {
                    val flattened = componentValue
                        .removePrefix("ComponentInfo{")
                        .removeSuffix("}")
                        .trim()
                    ComponentName.unflattenFromString(flattened)?.let { component ->
                        byComponent.putIfAbsent(component.flattenToString(), drawableValue)
                        byComponent.putIfAbsent(component.flattenToShortString(), drawableValue)
                        byPackage.putIfAbsent(component.packageName, drawableValue)
                    }
                }
            }
            event = parser.next()
        }
        return Entries(byComponent, byPackage)
    }

    private inline fun <T> XmlResourceParser.use(block: (XmlResourceParser) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
