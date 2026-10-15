package br.com.logicadopreco.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/** Amostra anônima binarizada do recorte do gráfico Quotex compartilhado pelo usuário.
 * São apenas categorias -1,0,1 (sem preços, navegador, credenciais ou pixels RGB).
 */
class QuotexVisualRegressionTest {
    @Test fun separatesMultipleCandlesFromUserChartInsteadOfOne() {
        val encoded = listOf(
            "eNrt3W2bmjgYgNHk//9o2qlWAYOgMJDkOfeXvbY7O1WOwfCeklorWwTYhV3YhV19sOfbP4bBAgnEnn/6i/6TRRJgtf7DfVPHHof9",
            "5n3Xxx6APU/ZE/Yw3+bYY7JPZvLYu2bP2LFjj4E+/jrHHo49YY/LnrBjx45d2NXmJht27MWfwI5d2IVd2NUye8LeLfs/UqMd+zTs",
            "2IVdzbLfTbHHUR9d/IA9kvpH7Owjsd+9DXrswq722Nc34LAHZTeh75V9emAGe5/kBfa1HuzZfW2wz9YgfSybCOwbwx6cPWHH3ht7",
            "59/tN/hP/q8NM/ncMPz/LZp+1zdHjPZh/Bo3bvtj/5z94Fe1d0o3WUpNs9/fSWXss1eVD/gE7Rjt83sUYseO/aC18MtJDgew334x",
            "9pDsny8c7Oex3xfw3j3i+fu1+8tUN7/O60vszRy3qZL9mKPdpTvK7tnCwR6O3Wg/n33/Sv5I9vEXUXvsOYdg3znlKqzkW2bPC3Pm",
            "mtmHb97hgTNt7NewD1+8wwPf0/gaqoWty7bYtxxgxN4P+8sCbps9j+4WjB07duzHTOkeL6cb9ute/GNevIN9PoX/LfbSlkWT7JNN",
            "k9Nf0ctW9tsDIYXdKQvD/NfYU4fsQ+3swzXsqVf24SL2vLhPrbRHfMqesEdkN9qbXcnvHu15dgrN0ewb9mW2z342/ofs99c2Hu3l",
            "B3wdyp46Zr8t0tPZU3q7Ai1vbZzLntbZh1bYh8e3eX4u0wvY84Y7xb3M68c3mMvPh/SOvvCPP9Mtdzra01Xsq6M9/b+c6Tnah8Iw",
            "z780zLd++ffOfvj1KhvG5ui7fX7voSl7wt4Ke8Iekn3zhAr7bvb5622CPWGPyF4Y7etTbewdjnbsHy7mhtgT9sDsCfvR7KNFir1n",
            "9sVFWjX7+Fhowh6GvbycsR/A/u6tHHaQC3t77Bl7YPZd16L3x17jRwA79mvZd936p7Jl2w/72/k89t7Y0+wa6E/ZN5/Q9mRP2K9n",
            "n12IdgK70d4Ue7putFfWeDJktJfYC4/iw94Q+/zSxHX2XHpcTxdPYaiZPV/GXryYoZqbW2M/mv1xKcZgtFfPvr7XZo39eZ3K5Ap1",
            "7NixN8eetrHf/k7s107iTx/t2JvYctvPXrroGHsf7JPri5fYUzD2TUcuO2Cf320CO/Zg7Cl1xl7aaVO6i9w6+/zOQtgrZH932B17",
            "v+zlx/W8Hi1dYH9dIAvsvYQ9T7f0sGNPh5xwix079ivZizt/sYdkT9gbYy+swt+xjw61Yu+E/YG/yD5HxX49edr7SOvpe/qQfeHc",
            "2r7UsW9kT9g7Z0/YQ7In7OHZ+2zOPvTDXnpYDvZ+2RfuSD35iajelbLPDnx9/HYS9nbZn49S+fZdrbEHr1b2fRcZvmEXduzYsWOP",
            "wV7NXhvs57m/HrLshZ3vyh6O2Sr/cvZ978nGWkj2ATt2Vcx+4B3/sLfAnrEb7dhjsGejHTt27NjP+Qhgx94se2FFJuzCrivY79Cl",
            "05uxd87+O8/lwI5d1a7knQuDXWezD32s5PUJ+3DqKXXYq4B/4FvJB2JPv8uesWMfreT7esIe9o2jPQ3YI7LbuVLHfP6ilbywC7uw",
            "C7taYp8+YRV7OHajHbuwqzH2xYdqL7Fb8hHZVQn7Pnrs2LFjx44dO3Zdzr53Po+9QfnXR2diDzTosWPHjh07duzYt7Hn0cO/LO1o",
            "7OGfyYZdFbF/ab+ZXUHYn/ciw943e8aOHTt29c1euE0N9m7Yx/eQLbEn7CHZnRKPXdhVCftwDLvrnhpj/3zXfIF9dLANe/3w6Zsj",
            "Movs0+12YVfl7Bk7duzYi+wZe9Psd03s2IvohccCYO+SPZfZb/86/gnsHbHn0ily0xuF55dfrFrZU4k9L4726dkT2Nse8tjDs08n",
            "bNixY8eOHTt27NixC7t6YJ/8J+zYsffBnrEHZH97jRP2vtkH7NixY8eOXdhVN3ueXMb2/CyktZtUYO+APY221bDHYR/9Gfag7LPb",
            "h7677yT29tlnFz+8Pf0de7Pw9/l6+bOwgoq97SZnSg9vjqZj75Y9bWZP2AOzC7tan+dZEtiFXdiFXdjV4ladRYBd2IVdkiRJkiRJ",
            "kiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJ",
            "kiRJkiRJknR6fwCVXD/I"
        ).joinToString("")
        val raw = InflaterInputStream(
            ByteArrayInputStream(Base64.getDecoder().decode(encoded))
        ).use { it.readBytes() }
        assertEquals(503 * 360, raw.size)
        val candles = CandleSegmenter.detect(raw, 503, 360)
        assertTrue("Expected dozens of candidates on the shared chart; found ${candles.size}",
            candles.size in 50..85)
        assertTrue(candles.last().x > 470)
    }
}
