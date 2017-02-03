package de.randombyte.lottery

import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.impl.AbstractEvent
import java.util.*

class DrawEvent(val winner: UUID, val pot: Double, private val cause : Cause) : AbstractEvent() {
    override fun getCause(): Cause = cause
}