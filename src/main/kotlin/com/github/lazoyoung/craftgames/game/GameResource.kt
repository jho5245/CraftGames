package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.script.ScriptFactory
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class GameResource(gameName: String) {

    var lobbyMap: GameMap

    val mapRegistry = HashMap<String, GameMap>()

    val scriptRegistry = HashMap<String, ScriptBase>()

    val restoreFile: File

    val tagFile: File

    /** CoordTags configuration across all maps. **/
    internal val tagConfig: YamlConfiguration

    /** Storage config for player inventory and spawnpoint. **/
    internal val restoreConfig: YamlConfiguration

    /** The root folder among all the resources in this game **/
    val root: Path

    init {
        // Read layout.yml
        val layoutFile: File
        val layoutConfig: YamlConfiguration
        val layoutPathStr = Main.config.getString("games.$gameName.layout")
                ?: throw GameNotFound("Game \'$gameName\' is not defined in config.yml")
        layoutFile = Main.instance.dataFolder.resolve(layoutPathStr)

        try {
            if (!layoutFile.isFile)
                throw FaultyConfiguration("Game \'$gameName\' does not have layout.yml")

            root = layoutFile.parentFile.toPath()
            layoutConfig = YamlConfiguration.loadConfiguration(BufferedReader(FileReader(layoutFile, Main.charset)))
        } catch (e: IOException) {
            throw FaultyConfiguration("Unable to read ${layoutFile.toPath()} for $gameName. Is it missing?", e)
        } catch (e: IllegalArgumentException) {
            throw FaultyConfiguration("File is empty: ${layoutFile.toPath()}")
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to resolve resource path.", e)
        }

        val mapSection = layoutConfig.getMapList("maps")
        val mapItr = mapSection.listIterator()
        val scriptItr = layoutConfig.getMapList("scripts").listIterator()
        var lobbyMap: GameMap? = null

        // Load maps from config
        while (mapItr.hasNext()) {
            val mutmap = mapItr.next().toMutableMap()
            val mapID = mutmap["id"] as String?
            val rawPath = mutmap["path"] as String?
            val repository: Path?  // Path to original map folder
            val lobby = mutmap["lobby"] as Boolean?
            var alias = mutmap["alias"] as String?  // Subname

            if (mapID == null) {
                Main.logger.config("Entry \'id\' of map is missing in ${layoutFile.toPath()}")
                continue
            }

            if (rawPath == null) {
                Main.logger.config("Entry 'path' of $mapID is missing in ${layoutFile.toPath()}")
                continue
            }

            if (alias == null) {
                mutmap["alias"] = mapID
                alias = mapID
                mapItr.set(mutmap)
                layoutConfig.set("maps", mapSection)
            }

            try {
                repository = root.resolve(rawPath)

                if (!Files.isDirectory(repository!!)) {
                    Main.logger.config("The map directory of \'$mapID\' is empty.")
                }
            } catch (e: InvalidPathException) {
                throw FaultyConfiguration("Unable to locate path to map '$mapID' for $gameName", e)
            } catch (e: SecurityException) {
                throw FaultyConfiguration("Unable to access file to map '$mapID' for $gameName", e)
            }

            val map = GameMap(mapID, alias, repository)
            mapRegistry[mapID] = map

            if (lobby == true) {
                lobbyMap = map
            }
        }
        layoutConfig.save(layoutFile)

        if (lobbyMap != null) {
            this.lobbyMap = lobbyMap
        } else {
            throw FaultyConfiguration("Game \'$gameName\' doesn't have lobby map.")
        }

        // Load scripts from config
        while (scriptItr.hasNext()) {
            val map = scriptItr.next()
            val scriptID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of script is missing in ${layoutFile.toPath()}")
            val pathStr = map["path"] as String? ?: throw FaultyConfiguration("Entry \'path\' of script $scriptID is missing in ${layoutFile.toPath()}")
            val scriptFile = layoutFile.parentFile!!.resolve(pathStr)

            try {
                if (!scriptFile.isFile)
                    throw FaultyConfiguration("Unable to locate the script: $scriptFile")
            } catch (e: SecurityException) {
                throw RuntimeException("Unable to read script: $scriptFile", e)
            }

            try {
                scriptRegistry[scriptID] = ScriptFactory.getInstance(scriptFile, null)
            } catch (e: ScriptEngineNotFound) {
                Main.logger.warning(e.localizedMessage)
            }
        }

        // Load coordinate tags, player restoration data
        val restorePath = layoutConfig.getString("players.path")
                ?: throw FaultyConfiguration("players.path is not defined in ${layoutFile.toPath()}.")
        val tagPath = layoutConfig.getString("coordinate-tags.path")
                ?: throw FaultyConfiguration("coordinate-tags.path is not defined in ${layoutFile.toPath()}.")

        restoreFile = layoutFile.parentFile!!.resolve(restorePath)
        tagFile = layoutFile.parentFile!!.resolve(tagPath)
        restoreFile.parentFile!!.mkdirs()

        if (!restoreFile.isFile && !restoreFile.createNewFile())
            throw RuntimeException("Unable to create file: ${restoreFile.toPath()}")
        if (!tagFile.isFile && !tagFile.createNewFile())
            throw RuntimeException("Unable to create file: ${tagFile.toPath()}")
        if (restoreFile.extension != "yml")
            throw FaultyConfiguration("This file has wrong extension: ${tagFile.name} (Rename it to .yml)")
        if (tagFile.extension != "yml")
            throw FaultyConfiguration("This file has wrong extension: ${tagFile.name} (Rename it to .yml)")

        restoreConfig = YamlConfiguration.loadConfiguration(restoreFile)
        tagConfig = YamlConfiguration.loadConfiguration(tagFile)
    }

    internal fun saveToDisk() {
        restoreConfig.save(restoreFile)
        tagConfig.save(tagFile)
    }
}