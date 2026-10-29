package com.lumen.tv.data

data class ContinueItem(
    val id: String,
    val title: String,
    val progressPercent: Int
)

interface ContinueRepository {
    fun getContinueItems(): List<ContinueItem>
}

class DemoContinueRepository : ContinueRepository {
    override fun getContinueItems(): List<ContinueItem> {
        return listOf(
            ContinueItem("1", "Aurora", 62),
            ContinueItem("2", "Midnight Protocol", 34),
            ContinueItem("3", "Sundown Valley", 78),
            ContinueItem("4", "Deep Field", 12),
            ContinueItem("5", "Neon Harbor", 54)
        )
    }
}
