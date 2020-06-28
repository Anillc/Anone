package com.an.one.verticles

import io.vertx.kotlin.core.file.readDirAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import java.util.jar.JarFile

class PluginVerticle : CoroutineVerticle() {
  override suspend fun start() {
    val fs = vertx.fileSystem()
    val files=fs.readDirAwait("plugins",".+\\.jar")
    val jarFile = JarFile(files[0])
//    val urlClassLoader = URLClassLoader()
  }
}
