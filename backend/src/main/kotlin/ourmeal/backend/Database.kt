package ourmeal.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import java.sql.PreparedStatement
import javax.sql.DataSource

class Database(val source: DataSource) {
  fun isReady(): Boolean = source.connection.use { connection ->
    connection.rows("SELECT to_regclass('city_catalog') IS NOT NULL AND to_regclass('price') IS NOT NULL") { it.getBoolean(1) }.single()
  }

  /** Run explicitly during local setup or an independently authorized deployment migration. */
  fun migrate() = transaction { connection ->
    connection.createStatement().use { statement ->
      statement.execute("SELECT pg_advisory_xact_lock(871412341)")
      val sql = checkNotNull(javaClass.getResource("/db/V1.sql")).readText()
      statement.execute(sql)
    }
  }

  fun <T> transaction(block: (Connection) -> T): T = source.connection.use { connection ->
    connection.autoCommit = false
    try { block(connection).also { connection.commit() } }
    catch (error: Throwable) { connection.rollback(); throw error }
  }

  companion object {
    fun pool(url: String, user: String, password: String): HikariDataSource = HikariDataSource(HikariConfig().apply {
      jdbcUrl = url
      username = user
      this.password = password
      maximumPoolSize = 8
      minimumIdle = 1
      connectionTimeout = 5000
      validationTimeout = 2000
      poolName = "ourmeal-prices"
    })
  }
}

internal fun PreparedStatement.bind(values: List<Any?>): PreparedStatement = apply {
  values.forEachIndexed { index, value -> setObject(index + 1, value) }
}

internal fun <T> Connection.rows(sql: String, vararg values: Any?, read: (ResultSet) -> T): List<T> =
  prepareStatement(sql).use { statement ->
    statement.bind(values.toList()).executeQuery().use { result -> buildList { while (result.next()) add(read(result)) } }
  }

internal fun Connection.execute(sql: String, vararg values: Any?): Int = prepareStatement(sql).use {
  it.bind(values.toList()).executeUpdate()
}
