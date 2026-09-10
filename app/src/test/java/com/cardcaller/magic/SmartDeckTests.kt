package com.cardcaller.magic

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SmartDeckTests {
    @Test fun commandSerializationMatchesFirmwareContract(){assertEquals("{\"command\":\"LED_ON\",\"color\":\"#006DFF\",\"brightness\":80}",SmartDeckCodec.encode(SmartDeckCommand.LedOn("#006dff",80)));assertEquals("{\"command\":\"LED_OFF\"}",SmartDeckCodec.encode(SmartDeckCommand.LedOff))}
    @Test fun eventDeserializationSupportsAllStatusFields(){assertEquals(SmartDeckEvent.DeckOpen(87),SmartDeckCodec.decodeEvent("{\"event\":\"DECK_OPEN\",\"battery\":87}"));assertEquals(SmartDeckEvent.LedState(true),SmartDeckCodec.decodeEvent("{\"event\":\"LED_STATE\",\"enabled\":true}"));assertNull(SmartDeckCodec.decodeEvent("broken"))}
    @Test fun deckOpenProducesLedOn(){val commands=SmartDeckAutomation.commandsFor(SmartDeckEvent.DeckOpen(80),SmartDeckSettings(),PlayingCard(Suit.SPADES,Rank.ACE));assertEquals(SmartDeckCommand.LedOn("#006DFF",80),commands.first())}
    @Test fun deckClosedAlwaysProducesLedOff(){assertEquals(listOf(SmartDeckCommand.LedOff),SmartDeckAutomation.commandsFor(SmartDeckEvent.DeckClosed(80),SmartDeckSettings(mode=SmartDeckMode.FINALE),null))}
    @Test fun duplicateSensorEventIsIgnoredWithinDebounce(){val gate=SensorEventGate(200);assertTrue(gate.accept("DeckOpen",1000));assertFalse(gate.accept("DeckOpen",1100));assertTrue(gate.accept("DeckOpen",1200))}
    @Test fun suitColorsMatchCardMapping(){assertEquals("#FF2038",CardLedEffectMapper.color(PlayingCard(Suit.HEARTS,Rank.ACE)));assertEquals("#FF7A20",CardLedEffectMapper.color(PlayingCard(Suit.DIAMONDS,Rank.TWO)));assertEquals("#006DFF",CardLedEffectMapper.color(PlayingCard(Suit.SPADES,Rank.THREE)));assertEquals("#16C060",CardLedEffectMapper.color(PlayingCard(Suit.CLUBS,Rank.FOUR)))}
    @Test fun disconnectTurnsLedOffInFakeDevice()=runTest{val fake=FakeSmartDeckDevice();fake.connect("FAKE");fake.send(SmartDeckCommand.LedOn("#006DFF",80));fake.disconnect();assertFalse(fake.snapshot.value.ledOn);assertEquals(SmartDeckCommand.LedOff,fake.commands.last())}
    @Test fun disarmPreventsAutomaticOpenInControllerContract()=runTest{val fake=FakeSmartDeckDevice();fake.connect("FAKE");fake.send(SmartDeckCommand.Disarm);assertFalse(fake.snapshot.value.armed);val commands=if(fake.snapshot.value.armed)SmartDeckAutomation.commandsFor(SmartDeckEvent.DeckOpen(80),SmartDeckSettings(),PlayingCard(Suit.SPADES,Rank.ACE))else emptyList();assertTrue(commands.isEmpty())}
    @Test fun autoOffSettingsAreLimitedTo120Seconds(){assertEquals(120,SmartDeckSettings(autoOffSeconds=999).sanitized().autoOffSeconds);assertEquals(1,SmartDeckSettings(autoOffSeconds=0).sanitized().autoOffSeconds)}
    @Test fun finaleSequenceTurnsOnBeforeEffect(){val card=PlayingCard(Suit.JOKER,Rank.JOKER,JokerColor.RED);val commands=SmartDeckAutomation.cardCommands(SmartDeckSettings(),card,true);assertTrue(commands[0] is SmartDeckCommand.LedOn);assertEquals(SmartDeckEffect.RAINBOW,(commands[1] as SmartDeckCommand.Effect).type)}
    @Test fun finaleWaitsUntilArmed(){val settings=SmartDeckSettings(mode=SmartDeckMode.FINALE);val card=PlayingCard(Suit.CLUBS,Rank.KING);assertTrue(SmartDeckAutomation.commandsFor(SmartDeckEvent.DeckOpen(90),settings,card,false).isEmpty());assertTrue(SmartDeckAutomation.commandsFor(SmartDeckEvent.DeckOpen(90),settings,card,true).isNotEmpty())}
    @Test fun fakeDeviceSupportsCompleteOpenCloseFlow()=runTest{val fake=FakeSmartDeckDevice();fake.connect("FAKE");fake.emit(SmartDeckEvent.DeckOpen(87),100);assertTrue(fake.snapshot.value.deckOpen);fake.send(SmartDeckCommand.LedOn("#006DFF",80));assertTrue(fake.snapshot.value.ledOn);fake.emit(SmartDeckEvent.DeckClosed(86),500);fake.send(SmartDeckCommand.LedOff);assertFalse(fake.snapshot.value.deckOpen);assertFalse(fake.snapshot.value.ledOn)}
}
