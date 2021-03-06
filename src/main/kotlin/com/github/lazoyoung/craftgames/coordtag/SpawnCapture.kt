package com.github.lazoyoung.craftgames.coordtag

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.math.RoundingMode

class SpawnCapture(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        mapID: String,
        index: Int? = null
) : CoordCapture(mapID, index) {

    override fun serialize() : String {
        val r = RoundingMode.HALF_UP
        val x = x.toBigDecimal().setScale(1, r)
        val y = y.toBigDecimal().setScale(1, r)
        val z = z.toBigDecimal().setScale(1, r)
        val yaw = this.yaw.toBigDecimal().setScale(1, r)
        val pitch = this.pitch.toBigDecimal().setScale(1, r)
        val builder = StringBuilder()

        for (e in arrayOf(x, y, z, yaw, pitch)) {
            builder.append(e).append(",")
        }

        return builder.removeSuffix(",").toString()
    }

    override fun toLocation(world: World): Location {
        return Location(world, x, y, z, yaw, pitch)
    }

    override fun teleport(player: Player) {
        val loc = Location(player.world, x, y, z, yaw, pitch)
        player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

}