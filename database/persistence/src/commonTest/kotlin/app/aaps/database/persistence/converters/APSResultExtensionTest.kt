package app.aaps.database.persistence.converters

import app.aaps.core.interfaces.aps.APSResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import app.aaps.database.entities.APSResult as DbAPSResult

/**
 * Tests for [APSResultExtension].
 *
 * Only the [APSResult.Algorithm] <-> [DbAPSResult.Algorithm] enum mapper is covered here.
 * The [APSResult] data mapping (`fromDb(Provider)` / `toDb()`) is not a plain field copy: it
 * relies on kotlinx-serialization JSON round-tripping and a `Provider<APSResult>`, and the
 * domain [APSResult] is an interface with no trivial constructor, so it is out of scope for a
 * pure JVM unit test.
 *
 * The algorithms both sides know are AMA, SMB, AUTO_ISF and AIMI; those are the ones that must
 * round trip. UNKNOWN is excluded - both mappers deliberately `error()` on it - and asserted
 * separately as a thrown exception.
 *
 * BOOST is deliberately left out of the round trip: the domain enum has it (the Boost plugins
 * produce it) but [DbAPSResult.Algorithm] does not, so it maps nowhere. See
 * [boostIsNotStorableInDatabase].
 */
internal class APSResultExtensionTest {

    private val roundTrippingAlgorithms = listOf(
        APSResult.Algorithm.AMA,
        APSResult.Algorithm.SMB,
        APSResult.Algorithm.AUTO_ISF,
        APSResult.Algorithm.AIMI
    )

    @Test
    fun algorithmRoundTripFromDomain() {
        roundTrippingAlgorithms.forEach { algorithm ->
            assertEquals(algorithm, algorithm.toDb().fromDb())
        }
    }

    @Test
    fun algorithmRoundTripFromDb() {
        val roundTrippingDb = DbAPSResult.Algorithm.entries.filter { it != DbAPSResult.Algorithm.UNKNOWN }
        roundTrippingDb.forEach { algorithm ->
            assertEquals(algorithm, algorithm.fromDb().toDb())
        }
    }

    @Test
    fun algorithmExplicitMapping() {
        assertEquals(DbAPSResult.Algorithm.AMA, APSResult.Algorithm.AMA.toDb())
        assertEquals(DbAPSResult.Algorithm.SMB, APSResult.Algorithm.SMB.toDb())
        assertEquals(DbAPSResult.Algorithm.AUTO_ISF, APSResult.Algorithm.AUTO_ISF.toDb())

        assertEquals(APSResult.Algorithm.AMA, DbAPSResult.Algorithm.AMA.fromDb())
        assertEquals(APSResult.Algorithm.SMB, DbAPSResult.Algorithm.SMB.fromDb())
        assertEquals(APSResult.Algorithm.AUTO_ISF, DbAPSResult.Algorithm.AUTO_ISF.fromDb())
    }

    @Test
    fun unknownAlgorithmThrows() {
        assertFailsWith<IllegalStateException> { APSResult.Algorithm.UNKNOWN.toDb() }
        assertFailsWith<IllegalStateException> { DbAPSResult.Algorithm.UNKNOWN.fromDb() }
    }

    /**
     * Pins a gap rather than a design. BOOST is a real algorithm in this app and the Boost plugins
     * set it on every result they produce, but the database enum was never given a matching entry,
     * so the mapper falls into its `else` branch. That makes the algorithm impossible to store.
     *
     * This test exists so the gap stays visible. When BOOST is added to
     * [DbAPSResult.Algorithm] and to both mappers, move it into [roundTrippingAlgorithms] and
     * delete this test.
     */
    @Test
    fun boostIsNotStorableInDatabase() {
        assertFailsWith<IllegalStateException> { APSResult.Algorithm.BOOST.toDb() }
    }
}
