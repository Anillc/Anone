package com.an.one.models

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.json.Json
import java.util.*

class ModelCodec<T> : MessageCodec<T, T> {

  private val name = UUID.randomUUID()

  @Suppress("UNCHECKED_CAST")
  private fun <T> cast(obj: Any?): T? {
    return obj as? T
  }

  override fun decodeFromWire(pos: Int, buffer: Buffer?): T? {
    val len = buffer?.getInt(pos) ?: return null
    val bytes = buffer.getBytes(pos + 4, pos + 4 + len)
    return cast(Json.decodeValue(String(bytes)))
  }

  override fun systemCodecID(): Byte {
    return -1
  }

  override fun encodeToWire(buffer: Buffer?, s: T?) {
    val value = Json.encode(s).toByteArray()
    buffer?.appendInt(value.size)
    buffer?.appendBytes(value)
  }

  override fun transform(s: T?): T? {
    return s
  }

  override fun name(): String {
    return "modelCodec $name"
  }
}
