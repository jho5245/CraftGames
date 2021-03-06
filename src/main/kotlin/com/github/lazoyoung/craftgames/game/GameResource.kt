package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.coordtag.AreaCapture
import com.github.lazoyoung.craftgames.coordtag.CoordTag
import com.github.lazoyoung.craftgames.coordtag.TagMode
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.exception.MapNotFound
import com.github.lazoyoung.craftgames.exception.ScriptEngineNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.script.ScriptFactory
import com.github.lazoyoung.craftgames.util.FileUtil
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * @throws GameNotFound
 */
class GameResource(val gameName: String) {

    lateinit var script: ScriptBase

    var lobbyMap: GameMap

    val mapRegistry = HashMap<String, GameMap>()

    /** CoordTags configuration across all maps. **/
    internal val tagConfig: YamlConfiguration

    /** Storage config for player inventory and spawnpoint. **/
    internal val restoreConfig: YamlConfiguration

    /** The root folder among all the resources in this game **/
    internal val root: Path

    private val restoreFile: File

    private val tagFile: File

    init {
        /*
         * Read layout.yml
         */
        val layoutFile: File
        val layoutConfig: YamlConfiguration
        val layoutPathStr = Main.config.getString("games.$gameName.layout")
                ?: throw GameNotFound("Game \'$gameName\' is not defined in config.yml")
        layoutFile = Main.instance.dataFolder.resolve(layoutPathStr)

        try {
            if (!layoutFile.isFile)
                throw FaultyConfiguration("Game \'$gameName\' does not have layout.yml")

            root = layoutFile.parentFile.toPath()
            layoutConfig = YamlConfiguration.loadConfiguration(FileUtil.getBufferedReader(layoutFile))
        } catch (e: IOException) {
            throw FaultyConfiguration("Unable to read ${layoutFile.toPath()} for $gameName. Is it missing?", e)
        } catch (e: IllegalArgumentException) {
            throw FaultyConfiguration("File is empty: ${layoutFile.toPath()}")
        } catch (e: InvalidPathException) {
            throw RuntimeException("Failed to resolve resource path.", e)
        }

        val mapItr = layoutConfig.getMapList("maps").listIterator()
        var lobbyMap: GameMap? = null

        /*
         * Load CoordTags, player restoration data
         */
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

        // Load tags into memory.
        CoordTag.reload(this)

        /*
         * Load maps from config
         */
        @Suppress("UNCHECKED_CAST")
        while (mapItr.hasNext()) {
            val mutmap = mapItr.next().toMutableMap()
            val mapID = mutmap["id"] as String?
            var alias = mutmap["alias"] as String?  // Subname
            val rawPath = mutmap["path"] as String?
            val repository: Path?  // Path to original map folder
            val lobby = mutmap["lobby"] as Boolean? ?: false

            val description: List<String> = when (val descRaw = mutmap["description"]) {
                is String -> {
                    listOf(descRaw)
                }
                is List<*> -> {
                    descRaw as List<String>
                }
                else -> {
                    listOf()
                }
            }

            if (mapID == null) {
                Main.logger.warning("Entry \'id\' of map is missing in ${layoutFile.toPath()}")
                continue
            }

            if (alias == null)
                alias = mapID

            if (rawPath == null) {
                Main.logger.warning("Entry 'path' of $mapID is missing in ${layoutFile.toPath()}")
                continue
            }

            try {
                repository = root.resolve(rawPath)
            } catch (e: InvalidPathException) {
                throw FaultyConfiguration("Unable to locate path to map '$mapID' for $gameName", e)
            }

            val areaRegistry = HashMap<String, List<AreaCapture>>()

            CoordTag.getAll(gameName).filter { it.mode == TagMode.AREA }.forEach {
                areaRegistry[it.name] = it.getCaptures(mapID) as List<AreaCapture>
            }

            val map = GameMap(mapID, alias, description, lobby, areaRegistry, repository)
            mapRegistry[mapID] = map

            if (lobby) {
                lobbyMap = map
            }
        }

        if (lobbyMap != null) {
            this.lobbyMap = lobbyMap
        } else {
            throw FaultyConfiguration("Game \'$gameName\' doesn't have lobby map.")
        }

        /*
         * Load scripts from config
         */
        val scriptPath = layoutConfig.getString("script.path")
                ?: throw FaultyConfiguration("Script is not defined in ${layoutFile.toPath()}")
        val scriptFile = layoutFile.parentFile!!.resolve(scriptPath)

        try {
            if (!scriptFile.isFile)
                throw FaultyConfiguration("Unable to locate the script: $scriptFile")
        } catch (e: SecurityException) {
            throw RuntimeException("Unable to read script: $scriptFile", e)
        }

        try {
            script = ScriptFactory.get(scriptFile)
        } catch (e: ScriptEngineNotFound) {
            Main.logger.warning(e.localizedMessage)
        }
    }

    internal fun saveToDisk(saveTag: Boolean) {
        // TODO Restore Module: Concurrent modification is not handled!
        restoreConfig.save(restoreFile)

        if (saveTag) {
            tagConfig.save(tagFile)
        }
    }

    /**
     * Look for playable maps and get a random element among them.
     * Lobby map is never obtained by using this method.
     *
     * @return A randomly chosen map.
     * @throws MapNotFound if this game doesn't have a map.
     */
    internal fun getRandomMap(): GameMap {
        val map: GameMap
        try {
            map = mapRegistry.filterValues { !it.isLobby }.values.random()
        } catch (e: NoSuchElementException) {
            throw MapNotFound("$gameName doesn't have a map.")
        }
        return map
    }
}