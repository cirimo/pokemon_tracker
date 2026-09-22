package net.pokedex.buildlogic

/**
 * Single source of truth for SDK levels.
 *
 * minSdk 26 is a locked product decision (docs/00-big-picture.md). It is also the reason
 * core library desugaring is on: java.time is unavailable below API 26 otherwise.
 */
object PokedexSdk {
    const val MIN = 26
    const val COMPILE = 36
    const val TARGET = 36
}
