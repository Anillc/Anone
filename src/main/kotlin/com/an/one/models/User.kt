package com.an.one.models

import com.an.one.utils.Utils
import java.io.Serializable
import java.util.*
import javax.persistence.*

@Entity
open class User(//修改后请到UserVerticle的update处添加属性
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  open var id: Long? = null,
  open var name: String,
  open var password: String,
  open var appUrl: String,
  @Column(columnDefinition = "text")
  open var msToken: String,
  open var msAppKey: String,
  open var msSecret: String,
  open var tokenRefreshCycle: Long,
  open var fileRefreshCycle: Long,
  open var jwtKey: String
) : Serializable {
  constructor() : this(
    0,
    "anone",
    Utils.hash("anone"),
    "",
    "",
    "",
    "",
    1000*60*60*24,
    1000*60*20,
    UUID.randomUUID().toString()
  )
}
