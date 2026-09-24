package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.backup.RecordInfo
import net.pokedex.core.model.backup.UNKNOWN_UPDATED_AT
import net.pokedex.core.model.backup.mergeRecords
import net.pokedex.core.model.backup.toCatchRecord
import net.pokedex.core.model.backup.toRecordInfo
import org.junit.Test

class BackupRecordsTest {

    private val lastWeek = 1_700_000_000_000L
    private val tonight = lastWeek + 7 * 24 * 3_600_000L

    @Test
    fun `a record survives the trip through the file field for field`() {
        val record = Fixtures.caught("unown", copy = 1).copy(notes = "second copy", favourite = true, priority = 2)

        assertThat(record.toRecordInfo().toCatchRecord()).isEqualTo(record)
    }

    @Test
    fun `the file carries updatedAt as an ISO instant`() {
        val info = Fixtures.caught("bulbasaur").copy(updatedAt = 0L).toRecordInfo()

        assertThat(info.updatedAt).isEqualTo("1970-01-01T00:00:00Z")
    }

    @Test
    fun `a record without updatedAt is older than anything local`() {
        val info = RecordInfo(variantId = "bulbasaur", caught = true)

        assertThat(info.toCatchRecord().updatedAt).isEqualTo(UNKNOWN_UPDATED_AT)
    }

    @Test
    fun `a mangled caughtAt is dropped rather than failing the record`() {
        val info = RecordInfo(variantId = "bulbasaur", caught = true, caughtAt = "last tuesday")

        val record = info.toCatchRecord()

        assertThat(record.caught).isTrue()
        assertThat(record.caughtAt).isNull()
    }

    @Test
    fun `merge does not let last week's file untick tonight's catch`() {
        val local = Fixtures.records(Fixtures.caught("bulbasaur").copy(updatedAt = tonight))
        val stale = Fixtures.caught("bulbasaur").copy(caught = false, updatedAt = lastWeek)

        assertThat(mergeRecords(local, listOf(stale))).isEmpty()
    }

    @Test
    fun `merge takes the file's record when the file changed it later`() {
        val local = Fixtures.records(Fixtures.caught("bulbasaur").copy(notes = null, updatedAt = lastWeek))
        val newer = Fixtures.caught("bulbasaur").copy(notes = "from the other phone", updatedAt = tonight)

        assertThat(mergeRecords(local, listOf(newer))).containsExactly(newer)
    }

    @Test
    fun `merge adds records this device has never seen`() {
        val incoming = Fixtures.caught("ivysaur").copy(updatedAt = UNKNOWN_UPDATED_AT)

        assertThat(mergeRecords(emptyMap(), listOf(incoming))).containsExactly(incoming)
    }

    @Test
    fun `merge leaves a record alone on a tie`() {
        val same = Fixtures.caught("bulbasaur")

        assertThat(mergeRecords(Fixtures.records(same), listOf(same.copy(notes = "differs")))).isEmpty()
    }

    @Test
    fun `merge keys on copy index, so the second unown is its own record`() {
        val local = Fixtures.records(Fixtures.caught("unown", copy = 0).copy(updatedAt = tonight))
        val second = Fixtures.caught("unown", copy = 1).copy(updatedAt = lastWeek)

        assertThat(mergeRecords(local, listOf(second))).containsExactly(second)
    }
}
