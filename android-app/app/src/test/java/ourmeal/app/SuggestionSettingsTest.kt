package ourmeal.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ourmeal.app.model.HIDDEN_DISH_CAP
import ourmeal.app.model.SUGGESTION_SETTINGS_VERSION
import ourmeal.app.model.SuggestionSettings

class SuggestionSettingsTest {
  private val gson = Gson()

  @Test
  fun hideAndUnhideAreIdempotent() {
    val hidden = SuggestionSettings().hide("lentjeas")
    assertSame(hidden, hidden.hide("lentjeas"))
    assertEquals(listOf("lentjeas"), hidden.hidden)
    val shown = hidden.unhide(setOf("lentjeas"))
    assertEquals(emptyList<String>(), shown.hidden)
    assertSame(shown, shown.unhide(setOf("lentjeas")))
  }

  @Test
  fun dropsTheOldestPastTheCap() {
    val settings = (0..HIDDEN_DISH_CAP).fold(SuggestionSettings()) { acc, index -> acc.hide("plato $index") }
    assertEquals(HIDDEN_DISH_CAP, settings.hidden.size)
    assertEquals("plato 1", settings.hidden.first())
    assertEquals("plato $HIDDEN_DISH_CAP", settings.hidden.last())
  }

  @Test
  fun survivesAGsonRoundTrip() {
    val settings = SuggestionSettings().hide("lentjeas").hide("pasta")
    val json = gson.toJson(settings)
    assertEquals("""{"version":1,"hidden":["lentjeas","pasta"]}""", json)
    assertEquals(settings, gson.fromJson(json, SuggestionSettings::class.java).sanitized())
  }

  @Test
  fun sanitizesMissingAndNullValues() {
    assertEquals(SuggestionSettings(), gson.fromJson("{}", SuggestionSettings::class.java).sanitized())
    assertEquals(
      SuggestionSettings(SUGGESTION_SETTINGS_VERSION, listOf("pasta")),
      gson.fromJson("""{"version":1,"hidden":[null," ","pasta","pasta"]}""", SuggestionSettings::class.java)
        .sanitized()
    )
  }
}
