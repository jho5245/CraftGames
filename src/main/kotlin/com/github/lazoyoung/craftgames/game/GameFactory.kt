package com.github.lazoyoung.craftgames.game

import com.github.lazoyoung.craftgames.Main
import com.github.lazoyoung.craftgames.exception.FaultyConfiguration
import com.github.lazoyoung.craftgames.exception.GameNotFound
import com.github.lazoyoung.craftgames.script.ScriptBase
import com.github.lazoyoung.craftgames.script.ScriptFactory
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

class GameFactory {
    companion object {
        private val runners: MutableMap<Int, Game> = HashMap()
        private var nextID = 0

        /**
         * Find the running games matching the parameter conditions.
         * You can avoid filtering a condition by passing null argument.
         *
         * @param name Accept the certain type of games only, if specified.
         * @param canJoin Accept the games where a player can join at this moment, if specified.
         * @return A list of games matching the conditions.
         */
        fun get(name: String? = null, canJoin: Boolean? = null) : List<Game> {
            return runners.values.filter {
                (name == null || it.name == name) && (canJoin == null || canJoin == it.canJoin())
            }
        }

        /**
         * Returns the running game matching the id. (Each game has its unique id)
         *
         * @param id Instance ID
         */
        fun getByID(id: Int) : Game? {
            return runners[id]
        }

        /**
         * Open a new running game with given name.
         *
         * @param name Classifies the type of game
         * @throws GameNotFound No such game exists with given id.
         * @throws FaultyConfiguration Configuration is not complete.
         * @throws RuntimeException Unexpected issue has arrised.
         */
        fun openNew(name: String) : Game {
            if (name.first().isDigit())
                throw FaultyConfiguration("Name should never start with number.")

            val reader: BufferedReader
            val layout: YamlConfiguration
            val scriptRegistry: MutableMap<String, ScriptBase> = HashMap()
            val path = Main.config.getString("games.$name.layout")
                    ?: throw GameNotFound("Game \'$name\' is not defined in config.yml")
            val file = Main.instance.dataFolder.resolve(path)

            try {
                if (!file.isFile)
                    throw FaultyConfiguration("Game \'$name\' does not have layout.yml")

                reader = BufferedReader(FileReader(file, Main.charset))
                layout = YamlConfiguration.loadConfiguration(reader)
            } catch (e: IOException) {
                throw FaultyConfiguration("Unable to read ${file.toPath()} for $name. Is it missing?", e)
            } catch (e: IllegalArgumentException) {
                throw FaultyConfiguration("File is empty: ${file.toPath()}")
            }

            val mapRegistry = layout.getMapList("maps")
            val confScripts = layout.getMapList("scripts")
            val mapItr = mapRegistry.listIterator()
            val scriptItr = confScripts.listIterator()

            while (mapItr.hasNext()) {
                val map = mapItr.next().toMutableMap()
                val mapID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of map is missing in ${file.toPath()}")
                if (!map.containsKey("alias")) {
                    map["alias"] = mapID; mapItr.set(map)
                    layout.set("maps", mapRegistry); layout.save(file)
                }
                if (!map.containsKey("path"))
                    throw FaultyConfiguration("Entry \'path\' of $mapID is missing in ${file.toPath()}")
            }

            while (scriptItr.hasNext()) {
                val map = scriptItr.next()
                val scriptID = map["id"] as String? ?: throw FaultyConfiguration("Entry \'id\' of script is missing in ${file.toPath()}")
                val pathStr = map["path"] as String? ?: throw FaultyConfiguration("Entry \'path\' of script $scriptID is missing in ${file.toPath()}")
                val scriptFile = Main.instance.dataFolder.resolve(pathStr)

                try {
                    if (!scriptFile.isFile)
                        throw FaultyConfiguration("Unable to locate the script: $scriptFile")
                } catch (e: SecurityException) {
                    throw RuntimeException("Unable to read script: $scriptFile", e)
                }

                scriptRegistry[scriptID] = ScriptFactory.getInstance(scriptFile, null)
            }

            val label = Main.config.getString("worlds.directory-label")!!
            val game: Game
            val map: GameMap

            Bukkit.getWorldContainer().listFiles()?.forEach {
                if (it.isDirectory && it.name.startsWith(label.plus('_'))) {
                    val id = Regex("(_\\d+)").findAll(it.name).last().value.drop(1).toInt()

                    // Prevents possible conflict with an existing folder
                    if (id >= nextID) {
                        nextID = id + 1
                    }
                }
            }
            game = Game(nextID, name, scriptRegistry)
            map = GameMap(game, mapRegistry)
            game.map = map
            runners[nextID++] = game
            return game
        }

        internal fun purge(id: Int) {
            runners.remove(id)
        }
    }
}