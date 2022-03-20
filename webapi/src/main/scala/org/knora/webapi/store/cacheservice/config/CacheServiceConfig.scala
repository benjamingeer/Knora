package org.knora.webapi.store.cacheservice.config

final case class CacheServiceConfig(enabled: Boolean, redis: RedisConfig)
final case class RedisConfig(host: String, port: Int)
