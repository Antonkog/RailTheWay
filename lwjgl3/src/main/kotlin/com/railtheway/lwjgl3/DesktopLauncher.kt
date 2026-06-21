package com.railtheway.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.railtheway.Config
import com.railtheway.RailGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Rail The Way")
        setWindowedMode(Config.VIEW_W.toInt(), Config.VIEW_H.toInt())
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(RailGame(), config)
}
