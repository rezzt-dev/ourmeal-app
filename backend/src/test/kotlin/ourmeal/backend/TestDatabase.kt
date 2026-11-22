package ourmeal.backend

import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/** Disposable PostgreSQL only. Refuses remote or ordinarily named databases before any mutation. */
class TestDatabase private constructor(val pool: HikariDataSource, private val container: PgContainer?) : AutoCloseable {
  val database = Database(pool)
  fun reset() = pool.connection.use { it.execute("TRUNCATE city_catalog,product_group,canonical_product,chain_product,price,alias_vote RESTART IDENTITY CASCADE") }
  override fun close() { pool.close(); container?.stop() }

  class PgContainer : PostgreSQLContainer<PgContainer>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))

  companion object {
    fun start(): TestDatabase {
      val url = System.getenv("OURMEAL_TEST_DB_URL")
      val runtime = if (url != null) {
        require(Regex("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):\\d+/ourmeal_test[a-z0-9_]*").matches(url)) {
          "Tests require an explicitly disposable local ourmeal_test database."
        }
        TestDatabase(Database.pool(url, System.getenv("OURMEAL_TEST_DB_USER") ?: "ourmeal_test", System.getenv("OURMEAL_TEST_DB_PASSWORD") ?: ""), null)
      } else {
        val container = PgContainer().withDatabaseName("ourmeal_test")
        container.start()
        TestDatabase(Database.pool(container.jdbcUrl, container.username, container.password), container)
      }
      runtime.database.migrate()
      return runtime
    }
  }
}
