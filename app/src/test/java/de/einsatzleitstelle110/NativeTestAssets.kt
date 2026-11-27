package de.einsatzleitstelle110

import java.io.File

object NativeTestAssets {
    private fun required(vararg paths: String): File =
        paths.map(::File).firstOrNull { it.isFile }
            ?: error("Pflicht-Testdatei fehlt. Erwartet: ${paths.joinToString(" oder ")}")

    fun bundled(name: String): File = required("src/main/native_assets/$name", "app/src/main/native_assets/$name")

    fun osm(name: String): File = required("../data/osm/$name", "data/osm/$name")
}
