package com.morepp.fm21touchsaveeditor.core
data class PlayerRecord(
    val recordId: Long, val name: String, val currentAbility: Int?,
    val potentialAbility: Int?, val attributes: Map<String, Int>
)
data class ClubFinance(val clubId: Long, val balance: Long?, val transferBudget: Long?)
data class CompetitionPrizeMoney(val competitionId: Long, val prizeByPosition: Map<Int, Long>)
data class LeagueReputation(val leagueId: Long, val reputation: Int)
sealed interface EditResult {
    data object Success : EditResult
    data class Rejected(val reason: String) : EditResult
}
object ValueValidation {
    fun ability(value: Int): EditResult =
        if (value in 1..200) EditResult.Success else EditResult.Rejected("Ability must be between 1 and 200")
    fun attribute(value: Int): EditResult =
        if (value in 1..20) EditResult.Success else EditResult.Rejected("Attribute must be between 1 and 20")
    fun reputation(value: Int): EditResult =
        if (value in 0..10000) EditResult.Success else EditResult.Rejected("Reputation is outside the supported range")
}
