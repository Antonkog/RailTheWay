package com.railtheway.teavm

import com.github.xpenatan.gdx.backends.teavm.config.TeaBuildConfiguration
import com.github.xpenatan.gdx.backends.teavm.config.TeaBuilder
import org.teavm.tooling.TeaVMTargetType
import org.teavm.tooling.TeaVMTool
import java.io.File

/**
 * Build-time tool (runs on the JVM, not transpiled): drives TeaVM to AOT-compile
 * the game into build/dist as index.html + JavaScript that runs in the browser.
 */
fun main() {
    val build = TeaBuildConfiguration()
    build.webappPath = File("build/dist").canonicalPath
    build.htmlTitle = "Rail The Way"
    build.htmlWidth = 960
    build.htmlHeight = 600
    build.useDefaultHtmlIndex = true
    build.targetType = TeaVMTargetType.JAVASCRIPT

    TeaBuilder.config(build)

    val tool = TeaVMTool()
    tool.mainClass = "com.railtheway.teavm.TeaVMLauncherKt"

    val ok = TeaBuilder.build(tool)
    if (!ok) {
        System.err.println("TeaVM build FAILED")
        System.exit(1)
    }
    println("TeaVM build complete -> " + build.webappPath)
}
