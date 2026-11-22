package ourmeal.prices.shared

import org.junit.Assert.*
import org.junit.Test

class ProductTextTest {
  @Test fun `quantities include multipacks decimal commas and dozens`() {
    assertEquals(Quantity("mass", 500.0), ProductText.quantity("Yogur griego 4x125g"))
    assertEquals(Quantity("mass", 1500.0), ProductText.quantity("Yogur 2 x 6 x 125 g"))
    assertEquals(Quantity("volume", 1500.0), ProductText.quantity("Aceite de oliva 1,5 litros"))
    assertEquals(Quantity("count", 12.0), ProductText.quantity("Papel higiénico 12 rollos"))
    assertEquals(Quantity("count", 6.0), ProductText.quantity("Huevos L media docena"))
    assertNull(ProductText.quantity("Huevos L"))
    assertNull(ProductText.quantity("Arroz"))
    assertNull(ProductText.quantity("Arroz 500g o 1kg"))
  }

  @Test fun `grouping removes brand and pack but preserves meaningful variants`() {
    assertEquals(ProductText.groupKey("Leche entera Marca A 1 L", "Marca A", "volume"),
      ProductText.groupKey("Leche entera Marca B 500 ml", "Marca B", "volume"))
    assertNotEquals(ProductText.groupKey("Leche entera 1 L", null, "volume"),
      ProductText.groupKey("Leche desnatada 1 L", null, "volume"))
    assertEquals("leche semidesnatada", ProductText.core("leche semi"))
    assertEquals("papel higienico", ProductText.core("papel water"))
    assertNotEquals(ProductText.normalize("Piña"), ProductText.normalize("Pina"))
  }

  @Test fun `hard constraints prevent dietary and unit substitutions`() {
    assertFalse(ProductText.compatible("leche desnatada", "leche entera 1 L"))
    assertFalse(ProductText.compatible("pan sin gluten", "pan integral"))
    assertFalse(ProductText.compatible("leche sin lactosa", "leche entera"))
    assertFalse(ProductText.compatible("agua sin gas", "agua con gas"))
    assertTrue(ProductText.compatible("yogures griegos", "yogur griego natural"))
  }

  @Test fun `display spelling and counted product names are preserved`() {
    assertEquals("Papel higiénico rollos", ProductText.displayName("Papel higiénico 12 rollos"))
    assertEquals("huevos frescos", ProductText.core("6 huevos frescos"))
    assertEquals("bolsas basura", ProductText.core("20 bolsas de basura"))
  }
}
