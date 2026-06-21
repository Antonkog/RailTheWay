package com.railtheway.teavm

import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration
import com.railtheway.RailGame

/**
 * Web entry point. TeaVM transpiles this (and everything it reaches) to JavaScript.
 * Binds the shared RailGame ApplicationListener to an HTML canvas.
 */
fun main() {
    val config = TeaApplicationConfiguration("canvas")
    config.width = 0   // 0 => auto-size to the canvas / page
    config.height = 0
    TeaApplication(RailGame(), config)
}
