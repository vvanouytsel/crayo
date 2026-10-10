package app.crayo.color

/**
 * A coloring page split into areas that can each get one color.
 *
 * Every closed shape on the page is a region. The bigger regions, and clusters of small ones
 * that sit together (hair strands, fingers), get a number. Those numbers are what the AI sees.
 */
class Regions(
    val width: Int,
    val height: Int,
    val gray: IntArray,           // brightness 0..255 for every pixel
    val label: IntArray,          // region for every pixel, 0 = on a line
    val nearest: IntArray,        // region for every pixel; line pixels take the closest region
    val regionCount: Int,
    val numberOf: IntArray,       // region -> number shown to the AI, 0 = none
    val numberCount: Int,
    val spotX: IntArray,          // per number: where to draw it (the roomiest point)
    val spotY: IntArray,
    val spotRoom: IntArray,       // per number: pixels of free space around that point
    val outside: BooleanArray,    // region is the empty paper around the drawing
    val touching: Map<Long, Int>, // pair of regions -> how much border they share
) {
    companion object {
        private const val LINE = 150          // darker than this is a line
        private const val MAX_NUMBERS = 90
        private const val TINY = 25           // regions smaller than this never get a number
        private const val FAR = 1 shl 28

        fun pair(a: Int, b: Int): Long = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()

        fun split(gray: IntArray, w: Int, h: Int): Regions {
            val n = w * h

            // Lines, made a little thicker so small gaps close and colors cannot leak out.
            var closed = BooleanArray(n) { gray[it] < LINE }
            repeat(2) { closed = grow(closed, w, h) }

            // Give every enclosed white area its own id.
            val label = IntArray(n)
            val stack = IntArray(n)
            var count = 0
            for (start in 0 until n) {
                if (closed[start] || label[start] != 0) continue
                count++
                var top = 0
                stack[top++] = start
                label[start] = count
                while (top > 0) {
                    val p = stack[--top]
                    val x = p % w
                    if (x > 0 && !closed[p - 1] && label[p - 1] == 0) { label[p - 1] = count; stack[top++] = p - 1 }
                    if (x < w - 1 && !closed[p + 1] && label[p + 1] == 0) { label[p + 1] = count; stack[top++] = p + 1 }
                    if (p >= w && !closed[p - w] && label[p - w] == 0) { label[p - w] = count; stack[top++] = p - w }
                    if (p < n - w && !closed[p + w] && label[p + w] == 0) { label[p + w] = count; stack[top++] = p + w }
                }
            }
            val area = IntArray(count + 1)
            for (l in label) area[l]++

            // The empty paper around the drawing: big areas that touch the edge of the page.
            val outside = BooleanArray(count + 1)
            fun edge(i: Int) { val l = label[i]; if (l != 0 && area[l] > n / 100) outside[l] = true }
            for (x in 0 until w) { edge(x); edge(n - w + x) }
            for (y in 0 until h) { edge(y * w); edge(y * w + w - 1) }

            // How far every pixel is from the nearest line (to find room for the numbers).
            val dist = IntArray(n) { if (closed[it]) 0 else FAR }
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (dist[i] == 0) continue
                var d = dist[i]
                if (x > 0) d = minOf(d, dist[i - 1] + 3)
                if (y > 0) {
                    d = minOf(d, dist[i - w] + 3)
                    if (x > 0) d = minOf(d, dist[i - w - 1] + 4)
                    if (x < w - 1) d = minOf(d, dist[i - w + 1] + 4)
                }
                dist[i] = d
            }
            for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
                val i = y * w + x
                if (dist[i] == 0) continue
                var d = dist[i]
                if (x < w - 1) d = minOf(d, dist[i + 1] + 3)
                if (y < h - 1) {
                    d = minOf(d, dist[i + w] + 3)
                    if (x < w - 1) d = minOf(d, dist[i + w + 1] + 4)
                    if (x > 0) d = minOf(d, dist[i + w - 1] + 4)
                }
                dist[i] = d
            }

            // Which regions sit next to each other, across a line.
            val touching = HashMap<Long, Int>()
            val close = HashSet<Long>()
            for (k in 1..8) {
                for (y in 0 until h) for (x in 0 until w - k) {
                    val a = label[y * w + x]; val b = label[y * w + x + k]
                    if (a != 0 && b != 0 && a != b) { val key = pair(a, b); touching[key] = (touching[key] ?: 0) + 1; if (k <= 5) close.add(key) }
                }
                for (y in 0 until h - k) for (x in 0 until w) {
                    val a = label[y * w + x]; val b = label[(y + k) * w + x]
                    if (a != 0 && b != 0 && a != b) { val key = pair(a, b); touching[key] = (touching[key] ?: 0) + 1; if (k <= 5) close.add(key) }
                }
            }

            // Big regions get a number each. Small ones that sit together share one.
            val bigEnough = (n * 0.0012).toInt()
            val parent = IntArray(count + 1) { it }
            fun root(i: Int): Int { var r = i; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
            fun small(i: Int) = !outside[i] && area[i] in TINY until bigEnough
            for (key in close) {
                val a = (key shr 32).toInt(); val b = key.toInt()
                if (small(a) && small(b)) parent[root(a)] = root(b)
            }
            val clusterArea = HashMap<Int, Int>()
            for (i in 1..count) if (small(i)) clusterArea[root(i)] = (clusterArea[root(i)] ?: 0) + area[i]

            data class Candidate(val size: Int, val region: Int, val cluster: Boolean)
            val candidates = ArrayList<Candidate>()
            for (i in 1..count) if (!outside[i] && area[i] >= bigEnough) candidates += Candidate(area[i], i, false)
            for ((clusterRoot, size) in clusterArea) if (size >= bigEnough / 2) candidates += Candidate(size, clusterRoot, true)
            candidates.sortByDescending { it.size }

            val numberOf = IntArray(count + 1)
            val clusterNumber = HashMap<Int, Int>()
            var numbers = 0
            for (candidate in candidates.take(MAX_NUMBERS)) {
                numbers++
                if (candidate.cluster) clusterNumber[candidate.region] = numbers else numberOf[candidate.region] = numbers
            }
            for (i in 1..count) if (small(i)) clusterNumber[root(i)]?.let { numberOf[i] = it }

            // Where to write each number: the spot with the most room.
            val spotX = IntArray(numbers + 1); val spotY = IntArray(numbers + 1); val spotRoom = IntArray(numbers + 1)
            for (i in 0 until n) {
                val number = numberOf[label[i]]
                if (number != 0 && dist[i] > spotRoom[number]) { spotRoom[number] = dist[i]; spotX[number] = i % w; spotY[number] = i / w }
            }
            for (number in 1..numbers) spotRoom[number] = spotRoom[number] / 3

            // Pixels on a line belong to the closest region, so the color reaches right up to the ink.
            val nearest = label.copyOf()
            val queue = IntArray(n)
            var head = 0; var tail = 0
            for (i in 0 until n) if (nearest[i] != 0) queue[tail++] = i
            while (head < tail) {
                val p = queue[head++]
                val x = p % w
                val l = nearest[p]
                if (x > 0 && nearest[p - 1] == 0) { nearest[p - 1] = l; queue[tail++] = p - 1 }
                if (x < w - 1 && nearest[p + 1] == 0) { nearest[p + 1] = l; queue[tail++] = p + 1 }
                if (p >= w && nearest[p - w] == 0) { nearest[p - w] = l; queue[tail++] = p - w }
                if (p < n - w && nearest[p + w] == 0) { nearest[p + w] = l; queue[tail++] = p + w }
            }

            return Regions(w, h, gray, label, nearest, count, numberOf, numbers, spotX, spotY, spotRoom, outside, touching)
        }

        private fun grow(mask: BooleanArray, w: Int, h: Int): BooleanArray {
            val out = mask.copyOf()
            val n = w * h
            for (i in 0 until n) {
                if (!mask[i]) continue
                val x = i % w
                if (x > 0) out[i - 1] = true
                if (x < w - 1) out[i + 1] = true
                if (i >= w) out[i - w] = true
                if (i < n - w) out[i + w] = true
            }
            return out
        }
    }

    /**
     * Paints the page. `colors` maps a number to a color (0xRRGGBB).
     * `wash` fades the colors toward white to save ink: 0 = full color, 1 = no color at all.
     * Returns one ARGB pixel per position, with the original lines on top.
     */
    fun paint(colors: Map<Int, Int>, wash: Float): IntArray {
        val unknown = -1
        val white = 0xFFFFFF
        val table = IntArray(regionCount + 1) { unknown }
        for (region in 1..regionCount) {
            if (outside[region]) table[region] = white
            else colors[numberOf[region]]?.let { table[region] = it }
        }

        // Small details without a number take the color of the neighbor they share most border with.
        val best = HashMap<Int, Pair<Int, Int>>()   // region -> (border size, color)
        fun vote(region: Int, neighbor: Int, size: Int) {
            if (table[region] != unknown || table[neighbor] == unknown || outside[neighbor]) return
            val current = best[region]
            if (current == null || size > current.first) best[region] = size to table[neighbor]
        }
        for ((key, size) in touching) {
            val a = (key shr 32).toInt(); val b = key.toInt()
            vote(a, b, size); vote(b, a, size)
        }
        for ((region, choice) in best) table[region] = choice.second
        for (region in 0..regionCount) if (table[region] == unknown) table[region] = white

        val out = IntArray(width * height)
        for (i in out.indices) {
            val color = table[nearest[i]]
            val shade = gray[i]
            val r = fade((color shr 16) and 0xFF, wash) * shade / 255
            val g = fade((color shr 8) and 0xFF, wash) * shade / 255
            val b = fade(color and 0xFF, wash) * shade / 255
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    private fun fade(channel: Int, wash: Float): Int = (channel + (255 - channel) * wash).toInt().coerceIn(0, 255)
}
