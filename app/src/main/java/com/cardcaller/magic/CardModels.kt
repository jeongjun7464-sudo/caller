package com.cardcaller.magic

enum class Suit(val symbol: String, val korean: String) { SPADES("♠","스페이드"), HEARTS("♥","하트"), CLUBS("♣","클럽"), DIAMONDS("♦","다이아몬드") }
enum class Rank(val label: String, val korean: String) { ACE("A","에이스"), TWO("2","2"), THREE("3","3"), FOUR("4","4"), FIVE("5","5"), SIX("6","6"), SEVEN("7","7"), EIGHT("8","8"), NINE("9","9"), TEN("10","10"), JACK("J","잭"), QUEEN("Q","퀸"), KING("K","킹") }
data class PlayingCard(val suit: Suit, val rank: Rank) {
    val id = "${suit.name}_${rank.name}"
    fun display(format: CardDisplayFormat, custom: String = "") = when(format) {
        CardDisplayFormat.SYMBOL -> "${suit.symbol} ${rank.label}"
        CardDisplayFormat.KOREAN -> "${suit.korean} ${rank.korean}"
        CardDisplayFormat.ENGLISH -> "${rank.name} OF ${suit.name}"
        CardDisplayFormat.IMAGE_ONLY -> "${suit.symbol}${rank.label}"
        CardDisplayFormat.CUSTOM -> custom.ifBlank { "${suit.symbol} ${rank.label}" }
    }
    companion object {
        val deck = Suit.entries.flatMap { s -> Rank.entries.map { PlayingCard(s, it) } }
        fun fromId(id: String?): PlayingCard? = deck.firstOrNull { it.id == id }
    }
}
enum class CardDisplayFormat { SYMBOL, KOREAN, ENGLISH, IMAGE_ONLY, CUSTOM }
enum class CallTheme { ANDROID, DARK, SIMPLE, CUSTOM }
data class AppSettings(val delaySeconds: Int = 3, val vibrate: Boolean = true, val ringtone: Boolean = true, val maxBrightness: Boolean = false, val displayFormat: CardDisplayFormat = CardDisplayFormat.SYMBOL, val callTheme: CallTheme = CallTheme.DARK, val gestureSensitivity: Float = 80f, val hideGestureResult: Boolean = true, val flipTrigger: Boolean = false, val volumeTrigger: Boolean = false, val clearLastCard: Boolean = true, val customCallerName: String = "")
data class RemoteMessage(val roomCode: String = "", val cardId: String = "", val senderId: String = "", val messageId: String = "", val createdAt: Long = 0, val consumedAt: Long? = null, val status: String = "pending")
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, CARD_RECEIVED, RECONNECTING }
