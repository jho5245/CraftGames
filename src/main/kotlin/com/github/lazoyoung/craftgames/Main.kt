package com.github.lazoyoung.craftgames

import com.github.lazoyoung.craftgames.command.CoordtagCommand
import com.github.lazoyoung.craftgames.command.GameAccessCommand
import com.github.lazoyoung.craftgames.command.GameCommand
import com.github.lazoyoung.craftgames.command.VoteCommand
import com.github.lazoyoung.craftgames.event.listener.GameListener
import com.github.lazoyoung.craftgames.util.MessengerUtil
import com.github.lazoyoung.craftgames.event.listener.ServerListener
import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.util.FileUtil
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.logging.Logger

class Main : JavaPlugin(), CommandExecutor {

    companion object {
        lateinit var config: FileConfiguration
            private set
        lateinit var instance: Main
            private set
        lateinit var charset: Charset
            private set
        lateinit var logger: Logger
            private set
    }

    override fun onEnable() {
        val gameCmd = getCommand("game")!!
        val ctCmd = getCommand("coord")!!
        val joinCmd = getCommand("join")!!
        val leaveCmd = getCommand("leave")!!
        val voteCmd = getCommand("mapvote")!!
        val gameExecutor = GameCommand()
        val ctExecutor = CoordtagCommand()
        val accessExecutor = GameAccessCommand()
        val voteExecutor = VoteCommand()
        val manager = Bukkit.getPluginManager()
        val messenger = Bukkit.getMessenger()
        instance = this
        Main.logger = logger


        loadConfig()
        loadAsset()
        gameCmd.setExecutor(gameExecutor)
        ctCmd.setExecutor(ctExecutor)
        joinCmd.setExecutor(accessExecutor)
        leaveCmd.setExecutor(accessExecutor)
        voteCmd.setExecutor(voteExecutor)
        gameCmd.tabCompleter = gameExecutor
        ctCmd.tabCompleter = ctExecutor
        joinCmd.tabCompleter = accessExecutor
        leaveCmd.tabCompleter = accessExecutor
        voteCmd.tabCompleter = voteExecutor
        manager.registerEvents(ServerListener(), this)
        manager.registerEvents(GameListener(), this)
        messenger.registerOutgoingPluginChannel(this, "BungeeCord")
        messenger.registerIncomingPluginChannel(this, "BungeeCord", MessengerUtil())
    }

    override fun onDisable() {
        // Close games
        Game.find().forEach { it.forceStop(async = false, error = false) }
    }

    private fun loadConfig() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        Main.config = config
        charset = Charset.forName(config.getString("file-encoding") ?: "UTF-8")
    }

    private fun loadAsset() {
        val root = dataFolder
        val sys: FileSystem
        val source: Path
        val target: Path

        if (config.getBoolean("install-sample")) {
            logger.info("Installing sample files...")

            try {
                sys = FileSystems.newFileSystem(file.toPath(), classLoader)
                source = sys.getPath("Sample")
                target = root.toPath()
            } catch (e: Exception) {
                e.printStackTrace()
                logger.severe("Unable to read jar files.")
                return
            }

            try {
                FileUtil.cloneFileTree(source, target)
            } catch (e: SecurityException) {
                e.printStackTrace()
                logger.severe("Access denied! Unable to install sample files.")
                return
            } catch (e: IOException) {
                e.printStackTrace()
                logger.severe("Error occurred! Unable to install sample files.")
                return
            }

            logger.info("Sample files have been installed!")
            config.set("install-sample", false)
            saveConfig()
        }
    }
}