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
}
