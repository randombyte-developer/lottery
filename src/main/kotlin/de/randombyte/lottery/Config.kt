package de.randombyte.lottery

import de.randombyte.kosp.extensions.*
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.text.TextTemplate
import org.spongepowered.api.text.TextTemplate.of
import org.spongepowered.api.text.action.TextActions
import java.time.Duration
import java.util.*

@ConfigSerializable
data class Config(
        @Setting(comment = "How much time between the draws") val drawInterval: Duration = Duration.ofHours(3),
        @Setting val ticketCosts: Int = 100,
        @Setting(comment = "How much of the pot should be given to the winner") val payoutPercentage: Int = 90,
        @Setting(comment = "The max. amount of tickets a player can buy") val maxTickets: Int = 5,
        @Setting val messages : Messages = Messages(),
        @Setting(comment = "Don't modify this!") val internalData: InternalData = InternalData(),
        @Setting val broadcasts: Broadcasts = Broadcasts()
) {
    fun calculatePot() = internalData.pot * (payoutPercentage / 100.0)
}

@ConfigSerializable
class Messages(
        @Setting(comment = "%currencyName;Is sent when executing the info command") val infoMessage: TextTemplate = of(
                "You currently have ".gray(),
                "boughtTickets".toArg().aqua(), " ticket(s) ".aqua(),
                "and there are ".gray(),
                "pot".toArg().aqua(), "currencySymbol".toArg(), " in the pot. ".gray(),

                "Buy a ticket with ".gray(),
                "/lottery buy".aqua().action(TextActions.suggestCommand("/lottery buy")), "! ".gray(),

                "The next draw is in ".gray(), "minutesUntilDraw".toArg().aqua(), " minutes".aqua(), ".".gray()
        ),
        @Setting(comment = "%;Is sent when buying (a) ticket(s)") val buyTicketMessage: TextTemplate = of(
                "You bought ".gray(), "boughtTickets".toArg(), " ticket(s)".gray(), " and now have a total amount of ".gray(),
                "totalTickets".toArg().aqua(), "ticket(s)".aqua()
        ),
        @Setting(comment = "%currencyName;Is broadcasted to all players when the pot was drawn") val drawMessageBroadcast: TextTemplate = of(
                "winnerName".toArg().aqua(),
                " won the lottery pot of ".gold(),
                "pot".toArg().aqua(), "currencySymbol".toArg(), "!".gold()
        ),
        @Setting(comment = "%pot;Is broadcasted to all player when a ticket was bought") val buyTicketBroadcast: TextTemplate = of(
                "buyerName".toArg().aqua(), " has bought ".gray(),
                "ticketAmount".toArg().aqua(), " ticket(s)! ".aqua(),

                "/lottery info".aqua().action(TextActions.suggestCommand("/lottery info"))
        ),
        @Setting(comment = "%currencyName;Is broadcasted to all players in the set broadcast interval") val broadcast: TextTemplate = of(
                "Current pot is at ".gray(), "pot".toArg().aqua(), "currencySymbol".toArg(), "! ".gray(),

                "Use ".gray(), "/lottery buy [amount]".aqua().action(TextActions.suggestCommand("/lottery buy")),
                " to buy tickets.".gray()
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
        @Setting(comment = "Broadcasts in set interval how much is in the pot; set to 0s to disable")
            val timedBroadcastInterval: Duration = Duration.ofMinutes(30)
)