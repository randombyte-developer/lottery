package de.randombyte.lottery

import de.randombyte.kosp.extensions.*
import de.randombyte.kosp.fixedTextTemplateOf
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextTemplate
import org.spongepowered.api.text.action.TextActions
import java.time.Duration
import java.util.*

@ConfigSerializable
data class Config(
        @Setting(comment = "How much time between the draws") val drawInterval: Duration = Duration.ofHours(3),
        @Setting val ticketCosts: Int = 100,
        @Setting(comment = "How much of the pot should be given to the winner") val payoutPercentage: Int = 90,
        @Setting(comment = "The max. amount of tickets a player can buy") val maxTickets: Int = 5,
        @Setting(comment = "Max. amount allowed to be added to the pot by a player") val maxDeposit: Int = 1000,
        @Setting val messages : Messages = Messages(),
        @Setting(comment = "Don't modify this!") val internalData: InternalData = InternalData(),
        @Setting val broadcasts: Broadcasts = Broadcasts()
) {
    fun calculatePot() = internalData.pot * (payoutPercentage / 100.0)
}

@ConfigSerializable
class Messages(
        @Setting(comment = "%currencyName,ticketCosts;Is sent when the info command is executed by a player") val infoMessagePlayer: TextTemplate = fixedTextTemplateOf(
                "You currently have ".gray(),
                "boughtTickets".toArg().aqua(), " ticket(s) ".aqua(),
                "and there are ".gray(),
                "pot".toArg().aqua(), "currencySymbol".toArg(), " in the pot. ".gray(),

                "Buy a ticket with ".gray(),
                "/lottery buy".aqua().action(TextActions.suggestCommand("/lottery buy")), "! ".gray(),

                "The next draw is in ".gray(), "minutesUntilDraw".toArg().aqua(), " minutes".aqua(), ".".gray()
        ),
        @Setting(comment = "%currencyName,ticketCosts;Is sent when the info command is executed by the console") val infoMessageConsole: TextTemplate = fixedTextTemplateOf(
                "There are ".gray(),
                "pot".toArg().aqua(), "currencySymbol".toArg(), " in the pot. ".gray(),

                "The next draw is in ".gray(), "minutesUntilDraw".toArg().aqua(), " minutes".aqua(), ".".gray()
        ),
        @Setting(comment = "%;Is sent when buying (a) ticket(s)") val buyTicketMessage: TextTemplate = fixedTextTemplateOf(
                "You bought ".gray(), "boughtTickets".toArg(), " ticket(s) and now have a total amount of ".gray(),
                "totalTickets".toArg().aqua(), "ticket(s)".aqua()
        ),
        @Setting(comment = "%currencyName;Is broadcasted to all players when the pot was drawn") val drawMessageBroadcast: TextTemplate = fixedTextTemplateOf(
                "winnerName".toArg().aqua(),
                " won the lottery pot of ".gold(),
                "pot".toArg().aqua(), "currencySymbol".toArg(), "!".gold()
        ),
        @Setting(comment = "%pot;Is broadcasted to all player when a ticket was bought") val buyTicketBroadcast: TextTemplate = fixedTextTemplateOf(
                "buyerName".toArg().aqua(), " has bought ".gray(),
                "ticketAmount".toArg().aqua(), " ticket(s)! ".aqua(),

                "/lottery info".aqua().action(TextActions.suggestCommand("/lottery info"))
        ),
        @Setting(comment = "%currencyName;Is broadcasted to all players in the set broadcast interval") val broadcast: TextTemplate = fixedTextTemplateOf(
                "Current pot is at ".gray(), "pot".toArg().aqua(), "currencySymbol".toArg(), "! ".gray(),

                "Use ".gray(), "/lottery buy [amount]".aqua().action(TextActions.suggestCommand("/lottery buy")),
                " to buy tickets.".gray()
        ),
        @Setting val notEnoughMoney: Text = "You do not have enough money to do that!".red()
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