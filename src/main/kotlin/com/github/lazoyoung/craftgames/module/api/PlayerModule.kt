package com.github.lazoyoung.craftgames.module.api

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.util.function.BiConsumer
import java.util.function.Predicate

interface PlayerModule {

    /**
     * The [trigger] executes when the given [Player] kills a [LivingEntity].
     *
     * @param killer is the only player binded to this trigger.
     * @param trigger The trigger that you want to add.
     */
    fun addKillTrigger(killer: Player, trigger: BiConsumer<Player, LivingEntity>)

    /**
     * The [trigger] executes when any [Player] kills a [LivingEntity].
     *
     * @param trigger The trigger that you want to add.
     */
    fun addKillTrigger(trigger: BiConsumer<Player, LivingEntity>)

    /**
     * The [trigger] executes right after the given [player] dies.
     *
     * Boolean value returned from the Predicate
     * determines whether the player respawns(true) or not(false).
     *
     * @param player This player is the only one binded to the trigger.
     * @param trigger The trigger that you want to add.
     */
    fun addDeathTrigger(player: Player, trigger: Predicate<Player>)

    /**
     * The [trigger] executes if any [Player] dies.
     *
     * Boolean value returned from the Predicate
     * determines whether the player respawns(true) or not(false).
     *
     * @param trigger The trigger that you want to add.
     */
    fun addDeathTrigger(trigger: Predicate<Player>)

    fun getPlayers(): List<Player>

    fun getTeamPlayers(team: Team): List<Player>

    fun getDeadPlayers(): List<Player>

    /**
     * Check if [player] is playing this game.
     */
    fun isOnline(player: Player): Boolean

    fun eliminate(player: Player)

    /**
     * Send [message] to [player]. Formatting codes are supported.
     *
     * Consult wiki about [Formatting codes](https://minecraft.gamepedia.com/Formatting_codes).
     */
    fun sendMessage(player: Player, message: String)

}