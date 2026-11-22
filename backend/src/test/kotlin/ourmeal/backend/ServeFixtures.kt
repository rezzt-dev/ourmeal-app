package ourmeal.backend

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.request.uri
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Local Android end-to-end target. Not included in any production jar or installation. */
fun main() {
  val runtime = TestDatabase.start()
  runtime.reset()
  val db = runtime.database
  val repository = CatalogRepository(db)
  repository.saveCity(Fixtures.city)
  val ingestion = Ingestion(repository, { Fixtures.Source() }, enabled = true)
  val engine = PriceEngine(repository, ingestion)
  val failNextResolve = AtomicBoolean(false)
  val failNextStores = AtomicBoolean(false)
  val server = embeddedServer(Netty, host = "127.0.0.1", port = 8080) {
    intercept(ApplicationCallPipeline.Plugins) {
      if ((call.request.uri.startsWith("/v1/resolve") && failNextResolve.compareAndSet(true, false)) ||
        (call.request.uri.startsWith("/v1/stores") && failNextStores.compareAndSet(true, false))) {
        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("code" to "TEST_TRANSIENT_FAILURE"))
        finish()
      }
    }
    priceApi(engine, Fixtures.LocationsFixture(), ingestion, db)
    routing {
      post("/__test/fail-next-resolve") {
        failNextResolve.set(true)
        call.respond(HttpStatusCode.NoContent)
      }
      post("/__test/fail-next-stores") {
        failNextStores.set(true)
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }.start()
  Runtime.getRuntime().addShutdownHook(Thread {
    server.stop(100, 2000)
    ingestion.close()
    runtime.close()
  })
  Files.writeString(Path.of("/tmp/ourmeal-fixture-server.pid"), ProcessHandle.current().pid().toString())
  println("LOCAL_SYNTHETIC_FIXTURE_API_READY port=8080")
  CountDownLatch(1).await()
}
