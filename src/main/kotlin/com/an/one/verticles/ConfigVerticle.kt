package com.an.one.verticles

import com.an.one.utils.EventAddresses.Companion.CONFIG_GET
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.file.readFileAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle

class ConfigVerticle : CoroutineVerticle() {
  private lateinit var configJson : JsonObject
  override suspend fun start() {
    configJson = JsonObject(vertx.fileSystem().readFileAwait("config.json"))
    vertx.eventBus().consumer(CONFIG_GET, this::get)
  }

  private fun get(message: Message<String>) {
    message.reply(configJson.map[message.body()])
  }
}
