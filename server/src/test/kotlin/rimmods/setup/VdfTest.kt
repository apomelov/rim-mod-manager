package rimmods.setup

import kotlin.test.Test
import kotlin.test.assertEquals

class VdfTest {
    @Test
    fun `parses libraryfolders with nested blocks and escaped backslashes`() {
        val vdf = Vdf.parse(
            """
            "libraryfolders"
            {
            	"0"
            	{
            		"path"		"C:\\Program Files (x86)\\Steam"
            		"apps"
            		{
            			"228980"		"1"
            		}
            	}
            	"1"  // a comment
            	{
            		"path"		"D:\\Games\\Steam"
            		"apps" { "294100" "964830569" }
            	}
            }
            """.trimIndent(),
        )
        val folders = vdf["libraryfolders"] as Vdf.Block
        assertEquals(listOf("0", "1"), folders.entries.map { it.first })
        val second = folders["1"] as Vdf.Block
        assertEquals(Vdf.Text("D:\\Games\\Steam"), second["path"])
        assertEquals(Vdf.Text("964830569"), (second["apps"] as Vdf.Block)["294100"])
    }

    @Test
    fun `a broken document parses as far as it goes`() {
        val vdf = Vdf.parse("\"a\" { \"b\" \"c\" ")
        assertEquals(Vdf.Text("c"), (vdf["a"] as Vdf.Block)["b"])
    }
}
