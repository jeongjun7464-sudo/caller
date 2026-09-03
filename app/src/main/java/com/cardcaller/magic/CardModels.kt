package com.cardcaller.magic

enum class Suit(val symbol: String, val korean: String) { SPADES("♠","스페이드"), HEARTS("♥","하트"), CLUBS("♣","클럽"), DIAMONDS("♦","다이아몬드"), JOKER("★","조커") }
enum class Rank(val label: String, val korean: String) { ACE("A","에이스"), TWO("2","2"), THREE("3","3"), FOUR("4","4"), FIVE("5","5"), SIX("6","6"), SEVEN("7","7"), EIGHT("8","8"), NINE("9","9"), TEN("10","10"), JACK("J","잭"), QUEEN("Q","퀸"), KING("K","킹"), JOKER("J","조커") }
enum class JokerColor { RED, BLACK }
data class PlayingCard(val suit: Suit, val rank: Rank, val jokerColor:JokerColor?=null) {
    val id = if(suit==Suit.JOKER) "JOKER_${jokerColor?.name?:JokerColor.BLACK.name}" else "${suit.name}_${rank.name}"
    val shortCode:String get() = if(suit==Suit.JOKER) if(jokerColor==JokerColor.RED) "JR" else "JB" else rank.label+when(suit){Suit.SPADES->"S";Suit.HEARTS->"H";Suit.DIAMONDS->"D";Suit.CLUBS->"C";Suit.JOKER->"J"}
    fun display(format: CardDisplayFormat, custom: String = "") = when(format) {
        CardDisplayFormat.SYMBOL -> "${suit.symbol} ${rank.label}"
        CardDisplayFormat.KOREAN -> "${suit.korean} ${rank.korean}"
        CardDisplayFormat.ENGLISH -> if(suit==Suit.JOKER) "${jokerColor?.name?:"BLACK"} JOKER" else "${rank.name} OF ${suit.name}"
        CardDisplayFormat.IMAGE_ONLY -> "${suit.symbol}${rank.label}"
        CardDisplayFormat.CUSTOM -> custom.ifBlank { "${suit.symbol} ${rank.label}" }
    }
    companion object {
        private val regularSuits=Suit.entries.filter{it!=Suit.JOKER};private val regularRanks=Rank.entries.filter{it!=Rank.JOKER}
        val deck = regularSuits.flatMap { s -> regularRanks.map { PlayingCard(s, it) } } + listOf(PlayingCard(Suit.JOKER,Rank.JOKER,JokerColor.RED),PlayingCard(Suit.JOKER,Rank.JOKER,JokerColor.BLACK))
        fun fromId(id: String?): PlayingCard? = deck.firstOrNull { it.id == id || it.shortCode.equals(id,true) }
    }
}
enum class CardDisplayFormat { SYMBOL, KOREAN, ENGLISH, IMAGE_ONLY, CUSTOM }
enum class CallTheme { ANDROID, DARK, SIMPLE, CUSTOM }
enum class TtsLanguage { KOREAN, ENGLISH }
enum class LabMode { PRACTICE, PERFORMANCE }
data class AppSettings(val delaySeconds: Int = 3, val vibrate: Boolean = true, val ringtone: Boolean = true, val maxBrightness: Boolean = false, val displayFormat: CardDisplayFormat = CardDisplayFormat.SYMBOL, val callTheme: CallTheme = CallTheme.DARK, val gestureSensitivity: Float = 80f, val hideGestureResult: Boolean = true, val flipTrigger: Boolean = false, val volumeTrigger: Boolean = false, val clearLastCard: Boolean = true, val customCallerName: String = "",val notificationTitle:String="카드 예언",val notificationContent:String="당신이 선택한 카드는 %CARD%입니다",val notificationDelay:Int=5,val ttsEnabled:Boolean=true,val ttsLanguage:TtsLanguage=TtsLanguage.KOREAN,val ttsSpeed:Float=1f,val ttsDelay:Int=1,val labMode:LabMode=LabMode.PRACTICE,val qrExpiryMinutes:Int=5)
data class RemoteMessage(val roomCode: String = "", val cardId: String = "", val senderId: String = "", val messageId: String = "", val createdAt: Long = 0, val consumedAt: Long? = null, val status: String = "pending")
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, CARD_RECEIVED, RECONNECTING }
enum class InstagramPublishType { STORY, FEED }
enum class InstagramPublishStatus { IDLE, CONFIRMING, PUBLISHING, SUCCEEDED, FAILED, RETRYING }
data class InstagramAccount(val username:String="연결되지 않음",val profileUrl:String="https://www.instagram.com/")
data class InstagramPublishResult(val requestId:String,val status:InstagramPublishStatus,val mediaId:String?=null,val permalink:String?=null,val createdAt:String?=null,val error:String?=null)
data class QrProphecy(val roomCode:String,val token:String,val qrUrl:String,val expiresAt:String,val cardId:String?=null,val consumed:Boolean=false)
data class AudienceEntry(val name:String,val prophecy:QrProphecy,val card:PlayingCard?,val revealed:Boolean=false)
