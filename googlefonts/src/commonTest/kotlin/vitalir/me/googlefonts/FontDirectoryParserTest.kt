package vitalir.me.googlefonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontDirectoryParserTest {

    private val fixture = """
        <?xml version="1.0" encoding="utf-8"?>
        <font_directory version='28'>
        	<families>
        		<family name='ABeeZee' menu='//fonts.gstatic.com/s/abeezee/v22/esDR31xSG-6AGleN2tOklQ.ttf'>
        			<font weight='400' width='100.0' italic='0.0' styleName='Regular' url='//fonts.gstatic.com/s/a/4ed0069c.ttf'/>
        			<font weight='400' width='100.0' italic='1.0' styleName='Regular Italic' url='//fonts.gstatic.com/s/a/fb76fef5.ttf'/>
        		</family>
        		<family name='Roboto' menu='//fonts.gstatic.com/s/roboto/v30/KFOmCnqEu92Fr1Mu4mxK.woff2'>
        			<font weight='100' width='100.0' italic='0.0' styleName='Thin' url='//fonts.gstatic.com/s/a/100.ttf'/>
        			<font weight='400' width='100.0' italic='0.0' styleName='Regular' url='//fonts.gstatic.com/s/a/400.ttf'/>
        			<font weight='700' width='100.0' italic='0.0' styleName='Bold' url='//fonts.gstatic.com/s/a/700.ttf'/>
        			<font weight='400' width='100.0' italic='1.0' styleName='Regular Italic' url='//fonts.gstatic.com/s/a/400i.ttf'/>
        		</family>
        	</families>
        </font_directory>
    """.trimIndent()

    @Test
    fun parsesFamiliesAndFonts() {
        val directory = FontDirectoryParser.parse(fixture)
        assertEquals(2, directory.families.size)
        val roboto = directory.families[1]
        assertEquals("Roboto", roboto.name)
        assertEquals("//fonts.gstatic.com/s/roboto/v30/KFOmCnqEu92Fr1Mu4mxK.woff2", roboto.menuUrl)
        assertEquals(4, roboto.fonts.size)
        val regular = roboto.fonts[1]
        assertEquals(400, regular.weight)
        assertEquals(false, regular.italic)
        assertEquals("Regular", regular.styleName)
        assertEquals("//fonts.gstatic.com/s/a/400.ttf", regular.url)
    }

    @Test
    fun parsesItalicFlag() {
        val directory = FontDirectoryParser.parse(fixture)
        val roboto = directory.families[1]
        assertTrue(roboto.fonts[3].italic)
    }

    @Test
    fun ignoresMalformedFontEntries() {
        val xml = """
            <font_directory>
            	<families>
            		<family name='Broken'>
            			<font weight='400' url='//fonts.gstatic.com/s/a/ok.ttf'/>
            			<font italic='0.0' styleName='NoWeight' url='//fonts.gstatic.com/s/a/no.ttf'/>
            			<font weight='700' italic='0.0' styleName='Bold'/>
            		</family>
            	</families>
            </font_directory>
        """.trimIndent()
        val directory = FontDirectoryParser.parse(xml)
        assertEquals(1, directory.families.size)
        assertEquals(1, directory.families[0].fonts.size)
    }
}