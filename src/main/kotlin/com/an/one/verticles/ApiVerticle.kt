package com.an.one.verticles

import com.an.one.utils.EventAddresses
import com.an.one.utils.Utils
import com.an.one.utils.Utils.Companion.user
import com.an.one.utils.end
import com.an.one.verticles.apis.MSFileVerticle
import com.an.one.verticles.apis.UserVerticle
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle

class ApiVerticle : CoroutineVerticle() {

  private lateinit var eventBus: EventBus
  private lateinit var router: Router
  private lateinit var jwtAuth: JWTAuth
  private lateinit var jwtAuthHandler: JWTAuthHandler
  override suspend fun start() {
    eventBus = vertx.eventBus()

    val user = user(eventBus) ?: throw Exception("unknown error")
    val port = eventBus.requestAwait<Long>(EventAddresses.CONFIG_GET, "port").body().toInt()
    jwtAuth = JWTAuth.create(
      vertx, JWTAuthOptions()
        .addPubSecKey(
          PubSecKeyOptions(
            JsonObject()
              .put("algorithm", "HS256")
              .put("publicKey", user.jwtKey)
              .put("symmetric", true)
          )
        )
    )
    jwtAuthHandler = JWTAuthHandler.create(jwtAuth)

    router = Router.router(vertx)
    initRoutes()
    vertx.createHttpServer()
      .requestHandler(router)
      .listenAwait(port)
  }

  private suspend fun initRoutes() {
    router.route().handler(BodyHandler.create())
    router.route().failureHandler(this::failure)

    vertx.deployVerticleAwait(UserVerticle(router, jwtAuth, jwtAuthHandler))
    vertx.deployVerticleAwait(MSFileVerticle(router))
  }

  private fun failure(ctx: RoutingContext) {
    if (ctx.statusCode() == 401) {
      ctx.response().setStatusCode(200).end(Utils.Reply(401, "请先登录"))
    }
  }

}
