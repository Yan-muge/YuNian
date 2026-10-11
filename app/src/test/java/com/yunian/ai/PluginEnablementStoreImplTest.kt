package com.yunian.ai

import com.yunian.ai.database.dao.AppMetaDao
import com.yunian.ai.database.model.AppMetaEntity
import com.yunian.ai.database.repository.AppMetaStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PluginEnablementStoreImpl] 的纯 JVM 单测（无 Robolectric）。
 *
 * 不碰 `android.util.Log`（日志走 [RecordingPluginEnablementStoreLog] 替身）、
 * 不碰 Room 运行时（[AppMetaDao] 是普通 interface，用内存 Map 实现它即可驱动**真实的**
 * [AppMetaStore] 与真实编解码路径）——与 `core:agent` 的 `CapabilityGrantTestFixtures` 同一手法。
 *
 * 重点覆盖契约的**硬要求**：读失败 / 首启 / 内容损坏 ⇒ 空集（= 全部启用）；
 * 另覆盖「出厂默认停用集」——键从未写过且指定默认集时返回该集，且用户开启后可持久。
 */
class PluginEnablementStoreImplTest {

    private class FakeDao(
        private val map: MutableMap<String, String> = mutableMapOf(),
        private val failGet: Throwable? = null,
        private val failPut: Throwable? = null,
    ) : AppMetaDao {

        var putCount: Int = 0
            private set

        override suspend fun get(key: String): String? {
            failGet?.let { throw it }
            return map[key]
        }

        override suspend fun put(entity: AppMetaEntity) {
            failPut?.let { throw it }
            putCount++
            map[entity.key] = entity.value
        }

        override suspend fun remove(key: String) {
            map.remove(key)
        }

        override suspend fun getAll(): List<AppMetaEntity> =
            map.map { (key, value) -> AppMetaEntity(key = key, value = value) }

        /** 绕过 store 直接塞原始内容（模拟历史脏数据 / KV 损坏）。 */
        fun putRaw(key: String, value: String) {
            map[key] = value
        }

        /** 直读原始内容（断言落盘格式用）。 */
        fun rawOf(key: String): String? = map[key]
    }

    private class RecordingPluginEnablementStoreLog : PluginEnablementStoreLog {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<Pair<String, Throwable?>>()

        override fun w(tag: String, message: String) {
            warnings += "$tag|$message"
        }

        override fun e(tag: String, message: String, throwable: Throwable?) {
            errors += "$tag|$message" to throwable
        }
    }

    private fun storeOn(dao: AppMetaDao, log: PluginEnablementStoreLog) =
        PluginEnablementStoreImpl(AppMetaStore(dao), log)

    // ── fail-safe：读不到 = 全部启用 ──────────────────────────────────────────

    @Test
    fun readFailureReturnsEmptySetSoEveryPluginStaysEnabled() = runBlocking {
        val log = RecordingPluginEnablementStoreLog()
        val store = storeOn(FakeDao(failGet = IllegalStateException("db closed")), log)

        assertEquals(emptySet<String>(), store.disabledIds())
        assertEquals(1, log.errors.size)
        assertTrue(log.errors.single().second is IllegalStateException)
    }

    @Test
    fun firstLaunchWithNoStoredValueReturnsEmptySet() = runBlocking {
        val store = storeOn(FakeDao(), RecordingPluginEnablementStoreLog())

        assertEquals(emptySet<String>(), store.disabledIds())
    }

    @Test
    fun blankStoredValueReturnsEmptySet() = runBlocking {
        val dao = FakeDao()
        dao.putRaw(PluginEnablementStoreImpl.KEY, "   \n\n  ")
        val store = storeOn(dao, RecordingPluginEnablementStoreLog())

        assertEquals(emptySet<String>(), store.disabledIds())
    }

    @Test
    fun corruptLinesAreDroppedAndGoodLinesSurvive() = runBlocking {
        val log = RecordingPluginEnablementStoreLog()
        val dao = FakeDao()
        dao.putRaw(PluginEnablementStoreImpl.KEY, "wechat.channel\n\tbad id here\n\nqqbot.channel\n")
        val store = storeOn(dao, log)

        assertEquals(setOf("wechat.channel", "qqbot.channel"), store.disabledIds())
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun fullyCorruptContentStillYieldsEmptySetNotFailure() = runBlocking {
        val dao = FakeDao()
        dao.putRaw(PluginEnablementStoreImpl.KEY, "not a plugin id\n\u0000\n")
        val store = storeOn(dao, RecordingPluginEnablementStoreLog())

        assertEquals(emptySet<String>(), store.disabledIds())
    }

    // ── 持久化语义 ────────────────────────────────────────────────────────────

    @Test
    fun disablingAPluginSurvivesARestart() = runBlocking {
        val dao = FakeDao()
        storeOn(dao, RecordingPluginEnablementStoreLog()).setEnabled("wechat.channel", false)

        // 新实例 + 新 store = 模拟进程重启后重新读盘
        val afterRestart = storeOn(dao, RecordingPluginEnablementStoreLog())
        assertEquals(setOf("wechat.channel"), afterRestart.disabledIds())
        assertEquals("wechat.channel", dao.rawOf(PluginEnablementStoreImpl.KEY))
    }

    @Test
    fun enablingAPluginRemovesItFromTheDisabledSet() = runBlocking {
        val dao = FakeDao()
        val store = storeOn(dao, RecordingPluginEnablementStoreLog())
        store.setEnabled("wechat.channel", false)
        store.setEnabled("qqbot.channel", false)

        store.setEnabled("wechat.channel", true)

        assertEquals(setOf("qqbot.channel"), storeOn(dao, RecordingPluginEnablementStoreLog()).disabledIds())
    }

    @Test
    fun repeatedIdenticalToggleDoesNotWriteAgain() = runBlocking {
        val dao = FakeDao()
        val store = storeOn(dao, RecordingPluginEnablementStoreLog())
        store.setEnabled("wechat.channel", false)
        val writesAfterFirst = dao.putCount

        store.setEnabled("wechat.channel", false)

        assertEquals(writesAfterFirst, dao.putCount)
    }

    @Test
    fun writeFailureIsSwallowedAndReportedNotThrown() = runBlocking {
        val log = RecordingPluginEnablementStoreLog()
        val store = storeOn(FakeDao(failPut = IllegalStateException("disk full")), log)

        // 不抛异常（契约：写失败由实现方自行记录并吞掉）
        store.setEnabled("wechat.channel", false)

        assertEquals(1, log.errors.size)
        assertEquals(emptySet<String>(), store.disabledIds())
    }

    @Test
    fun malformedPluginIdIsNeverWrittenToKv() = runBlocking {
        val log = RecordingPluginEnablementStoreLog()
        val dao = FakeDao()
        val store = storeOn(dao, log)

        store.setEnabled("bad id", false)
        store.setEnabled("", false)

        assertEquals(0, dao.putCount)
        assertNull(dao.rawOf(PluginEnablementStoreImpl.KEY))
        assertEquals(2, log.warnings.size)
    }

    @Test
    fun codecEncodesSortedOneIdPerLineAndRoundTrips() {
        val encoded = PluginEnablementCodec.encode(setOf("qqbot.channel", "wechat.channel"))

        assertEquals("qqbot.channel\nwechat.channel", encoded)
        val decoded = PluginEnablementCodec.decode(encoded)
        assertEquals(setOf("qqbot.channel", "wechat.channel"), decoded.ids)
        assertEquals(0, decoded.droppedLineCount)
    }

    @Test
    fun codecNeverThrowsOnNullInput() {
        val decoded = PluginEnablementCodec.decode(null)

        assertEquals(emptySet<String>(), decoded.ids)
        assertEquals(0, decoded.droppedLineCount)
        assertFalse(PluginEnablementCodec.isValidId(""))
        assertFalse(PluginEnablementCodec.isValidId("a b"))
        assertTrue(PluginEnablementCodec.isValidId("wechat.channel"))
    }

    // ── 出厂默认停用集（v2.2.0：默认关闭通道 + 非聊天类通用插件）──────────────────

    private fun storeWithFactoryDefaults(
        dao: AppMetaDao,
        defaults: Set<String> = setOf("channel.qqbot", "channel.wechat"),
    ) = PluginEnablementStoreImpl(
        AppMetaStore(dao),
        RecordingPluginEnablementStoreLog(),
        factoryDisabledIds = defaults,
    )

    @Test
    fun coldStartWithoutStoredValueReturnsFactoryDisabledSet() = runBlocking {
        val dao = FakeDao()
        val store = storeWithFactoryDefaults(dao)

        assertEquals(setOf("channel.qqbot", "channel.wechat"), store.disabledIds())
        // 只读不写：冷启动不落盘，只有在用户真正改动开关时才写 KV。
        assertNull(dao.rawOf(PluginEnablementStoreImpl.KEY))
    }

    @Test
    fun enablingFactoryDisabledPluginPersistsAcrossRestart() = runBlocking {
        val dao = FakeDao()
        val defaults = setOf("channel.qqbot", "channel.wechat")
        val store = storeWithFactoryDefaults(dao, defaults)

        // 用户把出厂关掉的 channel.qqbot 打开 → 落盘为「出厂集 − 该 id」。
        store.setEnabled("channel.qqbot", true)

        val afterRestart = storeWithFactoryDefaults(dao, defaults)
        assertEquals(setOf("channel.wechat"), afterRestart.disabledIds())
        assertEquals("channel.wechat", dao.rawOf(PluginEnablementStoreImpl.KEY))
    }

    @Test
    fun storedUserSetTakesPrecedenceOverFactoryDefaults() = runBlocking {
        val dao = FakeDao()
        // 用户此前动过开关：KV 里已是一份完整集合，出厂默认集不再参与。
        dao.putRaw(PluginEnablementStoreImpl.KEY, "channel.wechat")
        val store = storeWithFactoryDefaults(dao)

        assertEquals(setOf("channel.wechat"), store.disabledIds())
    }
}
