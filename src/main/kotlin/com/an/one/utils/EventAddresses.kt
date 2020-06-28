package com.an.one.utils

interface EventAddresses {
  companion object {
    const val MAIN_REDEPLOY_OAUTH = "com.an.one.main.redeployOAuth"
    const val MAIN_DEPLOY = "com.an.one.main.deploy"

    const val INIT_START = "com.an.one.init.start"

    const val DAO_GET_USER = "com.an.one.dao.getUser"
    const val DAO_UPDATE_USER = "com.an.one.dao.updateUser"

    const val CONFIG_GET = "com.an.one.config.get"

    const val OAUTH_GET_AUTH_URL = "com.an.one.oauth.getAuthUrl"
    const val OAUTH_AUTH = "com.an.one.oauth.auth"
    const val OAUTH_GET_TOKEN = "com.an.one.oauth.getToken"
    const val OAUTH_REFRESH = "com.an.one.oauth.refresh"
    const val OAUTH_STOP = "com.an.one.oauth.stop"

    const val MS_FILE_REFRESH = "com.an.one.msfile.refresh"
  }
}
