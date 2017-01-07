package de.randombyte.lottery

import de.randombyte.kosp.extensions.gray
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextTemplate
import org.spongepowered.api.text.TextTemplate.*
import org.spongepowered.api.text.format.TextColors
import java.time.Duration
import java.util.*

@ConfigSerializable
data class Config(
        @Setting(comment = DRAW_INTERVAL_COMMENT) val drawInterval: Duration = Duration.ofHours(3),
        @Setting val ticketCosts: Int = 100,
        @Setting(comment = "How much of the pot should be given to the winner") val payoutPercentage: Int = 90,
        @Setting(comment = "The max. amount of tickets a player can buy") val maxTickets: Int = 5,
        @Setting val messages : Messages = Messages(),
        @Setting(comment = "Don't modify this!") val internalData: InternalData = InternalData(),
        @Setting val broadcasts: Broadcasts = Broadcasts()
) {
    companion object {
        const val DRAW_INTERVAL_COMMENT = "Format explanation: http://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-"
    }
}

@ConfigSerializable
class Messages(
        @Setting val drawMessage: TextTemplate = of(
                arg("winnerName").color(TextColors.GOLD),
                " won the lottery pot of ".gray(),
                arg("pot").color(TextColors.AQUA),
                arg("currencySymbol")
        ),
        @Setting val infoMessage: TextTemplate = of(
                "You currently have ".gray(),
                arg("boughtTickets").color(TextColors.AQUA),

        )
)

@ConfigSerializable
data class InternalData(
        @Setting val boughtTickets: Map<UUID, Int> = emptyMap(),
        @Setting val pot: Int = 0
) {
    fun getBoughtTickets(player: Player) = boughtTickets[player.uniqueId] ?: 0
}

@ConfigSerializable
data class Broadcasts(
        @Setting(comment = "Broadcasts how to buy tickets when someone bought one")
            val broadcastTicketPurchase: Boolean = false,
        @Setting(comment = "Broadcasts in set interval how much is in the pot; set to PT0M to disable")
            val timedBroadcastInterval: Duration = Duration.ofMinutes(30)
)