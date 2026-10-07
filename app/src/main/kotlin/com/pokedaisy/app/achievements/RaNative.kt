package com.pokedaisy.app.achievements

/**
 * JNI surface of `pokedaisy_ra.c` (rcheevos' rc_client). [RetroAchievements] is
 * the only caller; the `@JvmStatic` functions are called back from native code.
 *
 * Threads: [raDoFrame], [raLoadGame], [raUnloadGame], [raSerializeProgress] and
 * [raDeserializeProgress] read the player's core, so they run on the emu thread
 * only. [raPump] hands queued server answers to rc_client - the emu thread while a
 * game runs, the HTTP thread otherwise (see [RetroAchievements.deliver]).
 */
internal object RaNative {
    init {
        System.loadLibrary("pokedaisy")
    }

    external fun raInit(): Boolean
    /** `rcheevos/12.5`, for the end of the User-Agent. */
    external fun raUserAgentClause(): String
    external fun raQueueResponse(id: Int, status: Int, body: ByteArray?)
    external fun raPump()

    external fun raLoginWithPassword(user: String, password: String)
    external fun raLoginWithToken(user: String, token: String)
    external fun raLogout()

    external fun raLoadGame(): Boolean
    external fun raUnloadGame()
    external fun raDoFrame()
    external fun raIdle()
    external fun raReset()
    external fun raSerializeProgress(): ByteArray?
    external fun raDeserializeProgress(data: ByteArray?)
    /** The loaded game's achievements, see [parseAchievementList]; null = no game / none. */
    external fun raAchievementList(): ByteArray?
    /** The loaded game's leaderboards, see [parseLeaderboardList]; null = none. */
    external fun raLeaderboardList(): ByteArray?
    /** Leaderboard [id]'s entries: [count] from rank [first], or around the player; answers in [onLeaderboardEntries]. */
    external fun raFetchLeaderboard(token: Int, id: Int, aroundUser: Boolean, first: Int, count: Int)
    /** The player's unlock counts for every GBA game; answers in [onAllProgress]. */
    external fun raFetchAllProgress(token: Int)
    /** Debug builds: one line on what rc_client sees (see the native side). */
    external fun raDebugStatus(): String

    // --- called from native ---

    @JvmStatic fun serverCall(id: Int, url: String, postData: String?, contentType: String?) =
        RetroAchievements.serverCall(id, url, postData, contentType)

    @JvmStatic fun onLogin(
        result: Int, error: String?, user: String?, displayName: String?, token: String?,
        avatarUrl: String?, score: Int, softcoreScore: Int,
    ) = RetroAchievements.onLogin(result, error, user, displayName, token, avatarUrl, score, softcoreScore)

    @JvmStatic fun onGameLoaded(
        result: Int, error: String?, gameId: Int, title: String?, badgeUrl: String?, hash: String?,
        achievements: Int, unlocked: Int, points: Int, pointsUnlocked: Int,
    ) = RetroAchievements.onGameLoaded(result, error, gameId, title, badgeUrl, hash, achievements, unlocked, points, pointsUnlocked)

    @JvmStatic fun onEvent(
        type: Int, id: Int, title: String?, description: String?, badgeUrl: String?, extra: String, points: Int,
    ) = RetroAchievements.onEvent(RaEvent(type, id, title, description, badgeUrl, extra, points))

    @JvmStatic fun onLog(message: String?) = RetroAchievements.onLog(message)

    @JvmStatic fun onLeaderboardEntries(
        token: Int, aroundUser: Boolean, result: Int, error: String?, entries: ByteArray?, total: Int, userIndex: Int,
    ) = RetroAchievements.onLeaderboardEntries(token, aroundUser, result, error, entries, total)

    @JvmStatic fun onAllProgress(token: Int, result: Int, entries: ByteArray?) =
        RetroAchievements.onAllProgress(token, result, entries)
}
