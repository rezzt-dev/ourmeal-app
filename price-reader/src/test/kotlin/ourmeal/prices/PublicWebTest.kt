package ourmeal.prices

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class PublicWebTest {
  @Test fun `untrusted OSM websites cannot contain credentials local hosts or unsafe schemes`() {
    listOf("http://shop.example", "file:///etc/passwd", "javascript:alert(1)", "https://user:pass@shop.example", "https://127.0.0.1", "https://localhost", "https://printer.local", "https://[::1]", "https://shop.example:8443").forEach {
      assertNull(it, publicWebsite(it))
    }
    assertEquals("https://shop.example/", publicWebsite("shop.example"))
  }

  @Test fun `DNS answers cannot point to private or reserved networks`() {
    listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1", "fe80::1").forEach {
      assertFalse(it, isPublicAddress(InetAddress.getByName(it)))
    }
    assertTrue(isPublicAddress(InetAddress.getByName("1.1.1.1")))
    assertTrue(isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
  }
}
