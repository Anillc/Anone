package com.an.one.verticles

import com.an.one.utils.EventAddresses.Companion.INIT_START
import com.an.one.utils.EventAddresses.Companion.MAIN_DEPLOY
import com.an.one.utils.Utils
import com.an.one.utils.end
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.file.writeFileAwait
import io.vertx.kotlin.core.http.closeAwait
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.core.undeployAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.launch
import java.io.File

class InitVerticle : CoroutineVerticle() {
  private val configFile = File("config.json")
  private lateinit var eventBus: EventBus
  private var httpServer: HttpServer? = null
  override suspend fun start() {
    eventBus = vertx.eventBus()
    eventBus.consumer<Unit>(INIT_START, this::start0)
  }

  private fun start0(message: Message<Unit>) {
    launch {
      if (configFile.exists()) {
        deployVerticles()
        return@launch
      }
      val router = Router.router(vertx)
      router.route().handler(BodyHandler.create())
      router.post("/init").handler(this@InitVerticle::init)
      router.route().handler(this@InitVerticle::needInit)
      httpServer = vertx.createHttpServer()
        .requestHandler(router)
        .listenAwait(8080)
    }
  }

  override suspend fun stop() {
    httpServer?.closeAwait()
  }

  private fun init(ctx: RoutingContext) {
    launch {
      val body = ctx.bodyAsJson
      if (body == null) {
        ctx.response().end(Utils.Reply(400, "Bad request"))
        return@launch
      }
      val sqlUrl = body.getString("url")
      val sqlUser = body.getString("user")
      val sqlPwd = body.getString("password")
      val port = body.getInteger("port", 8080)
      if (sqlUrl == null || sqlUser == null || sqlPwd == null) {
        ctx.response().end(Utils.Reply(400, "Bad request"))
        return@launch
      }
      val configJson = JsonObject()
      configJson.put("sql_url", sqlUrl)
      configJson.put("sql_user", sqlUser)
      configJson.put("sql_password", sqlPwd)
      configJson.put("port", port)
      try {
        vertx.fileSystem().writeFileAwait("config.json", configJson.toBuffer())
        ctx.response().end(Utils.Reply(200, "successful"))
        deployVerticles()
      } catch (e: Exception) {
        ctx.response().end(Utils.Reply(500, e.message))
      }
    }
  }

  private fun needInit(ctx: RoutingContext) {
    ctx.response().end(Utils.Reply(-1, "please init"))
  }

  private suspend fun deployVerticles() {
    vertx.undeployAwait(deploymentID)
    eventBus.publish(MAIN_DEPLOY, null)
  }
}
