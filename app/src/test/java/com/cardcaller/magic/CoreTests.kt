package com.cardcaller.magic
import org.junit.Assert.*
import org.junit.Test
class CoreTests {
 @Test fun deckHas52UniqueCards(){assertEquals(52,PlayingCard.deck.size);assertEquals(52,PlayingCard.deck.map{it.id}.toSet().size)}
 @Test fun idRoundTrip(){PlayingCard.deck.forEach{assertEquals(it,PlayingCard.fromId(it.id))}}
 @Test fun tapMapping(){assertEquals(Rank.ACE,GestureInputManager.rank(1));assertEquals(Rank.TEN,GestureInputManager.rank(10));assertEquals(Rank.KING,GestureInputManager.rank(13))}
 @Test fun invalidTaps(){assertNull(GestureInputManager.rank(0));assertNull(GestureInputManager.rank(14))}
 @Test fun dragMapping(){assertEquals(Suit.SPADES,GestureInputManager.suit(0f,-100f,50f));assertEquals(Suit.HEARTS,GestureInputManager.suit(100f,0f,50f));assertEquals(Suit.CLUBS,GestureInputManager.suit(0f,100f,50f));assertEquals(Suit.DIAMONDS,GestureInputManager.suit(-100f,0f,50f));assertNull(GestureInputManager.suit(2f,3f,50f))}
 @Test fun duplicateConsumption(){val d=MessageDeduplicator();assertTrue(d.shouldConsume("x"));assertFalse(d.shouldConsume("x"))}
 @Test fun roomCodes(){repeat(100){assertTrue(RoomCode.valid(RoomCode.generate()))};assertFalse(RoomCode.valid("123"));assertFalse(RoomCode.valid("abcdef"))}
 @Test fun notificationTemplateRevealsSelectedCard(){assertEquals("예언: 하트 에이스",MagicLabLogic.notificationContent("예언: %CARD%",PlayingCard(Suit.HEARTS,Rank.ACE)))}
 @Test fun ttsSupportsKoreanAndEnglish(){val c=PlayingCard(Suit.SPADES,Rank.KING);assertTrue(MagicLabLogic.ttsPhrase(c,TtsLanguage.KOREAN).contains("스페이드 킹"));assertTrue(MagicLabLogic.ttsPhrase(c,TtsLanguage.ENGLISH).contains("KING OF SPADES"))}
 @Test fun lieDetectorAlwaysReturnsPreselectedCard(){val c=PlayingCard(Suit.CLUBS,Rank.SEVEN);assertEquals(c,MagicLabLogic.forcedLieDetectorResult(c,listOf(true,false,true)))}
 @Test fun photoTransformIsConstrained(){val t=MagicLabLogic.sanitizeTransform(OverlayTransform(x=2f,y=-1f,alpha=3f,rotation=300f,shadow=-4f));assertEquals(1f,t.x);assertEquals(0f,t.y);assertEquals(1f,t.alpha);assertEquals(180f,t.rotation);assertEquals(0f,t.shadow)}
}
