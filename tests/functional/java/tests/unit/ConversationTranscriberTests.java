//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
package tests.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URI;


import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;


import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SessionEventArgs;
import com.microsoft.cognitiveservices.speech.Recognizer;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.conversation.ConversationTranscriptionResult;
import com.microsoft.cognitiveservices.speech.conversation.ConversationTranscriber;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.OutputFormat;
import com.microsoft.cognitiveservices.speech.Connection;
import com.microsoft.cognitiveservices.speech.conversation.Participant;
import com.microsoft.cognitiveservices.speech.conversation.User;

import tests.Settings;
import tests.TestHelper;

public class ConversationTranscriberTests {
    private final Integer FIRST_EVENT_ID = 1;
    private AtomicInteger eventIdentifier = new AtomicInteger(FIRST_EVENT_ID);
    private String inroomEndpoint = "wss://its.demo.princeton.customspeech.ai/speech/recognition/multiaudio?";
    @BeforeClass
    static public void setUpBeforeClass() throws Exception {
        // Override inputs, if necessary
        Settings.LoadSettings();
    }

    // -----------------------------------------------------------------------
    // ---
    // -----------------------------------------------------------------------

    @Ignore
    @Test
    public void testDispose() {
        // TODO: make dispose method public
        fail("dispose not yet public");
    }

    @Test
    public void testConversationIdWithAnsiOnly() {
        SpeechConfig s = SpeechConfig.fromSubscription(Settings.SpeechSubscriptionKey, Settings.SpeechRegion);
        assertNotNull(s);

        ConversationTranscriber t = new ConversationTranscriber(s, AudioConfig.fromWavFileInput(Settings.WavFile8Channels));
        assertNotNull(t);
        assertNotNull(t.getTranscriberImpl());
        assertTrue(t instanceof Recognizer);

        String myConversationId = "123 456";
        t.setConversationId(myConversationId);
        String gotId = t.getConversationId();
        assertEquals(myConversationId, gotId);
    }

    @Test
    public void testConversationCreateUsers() {
        String myId = "xyz@example.com";
        User user = User.fromUserId(myId);
        assertEquals(myId, user.getId());
    }

    @Test
    public void testConversationCreateParticipants() {
        String emptyString = "";
        Participant a = Participant.from("xyz@example.com");
        a.setPreferredLanguage("zh-cn");
        boolean exception = false;
        try {
            a.setPreferredLanguage("");
        }
        catch (Exception ex) {
            System.out.println("Got Exception in setPreferredLanguage:" + ex.toString());
            exception = true;
        }
        assertEquals(exception, true);

        // Voice signature format as specified here https://aka.ms/cts/signaturegenservice
        String voice = "1.1, 2.2";

        a.setVoiceSignature(voice);        

        exception = false;
        try {
            a.setVoiceSignature(emptyString);
        }
        catch (Exception ex) {
            System.out.println("Got Exception in setVoiceSignature:" + ex.toString());
            exception = true;
        }
        assertEquals(exception, true);

        assertEquals(exception, true);
    }

    @Test
    public void testConversationAddParticipant() throws InterruptedException, ExecutionException, TimeoutException {
        SpeechConfig s = SpeechConfig.fromEndpoint(URI.create(inroomEndpoint), Settings.SpeechSubscriptionKey);
        assertNotNull(s);

        WavFileAudioInputStream ais = new WavFileAudioInputStream(Settings.WavFile8Channels);
        assertNotNull(ais);

        ConversationTranscriber t = new ConversationTranscriber(s, AudioConfig.fromStreamInput(ais));
        assertNotNull(t);
        assertNotNull(t.getTranscriberImpl());
        assertTrue(t instanceof Recognizer);

        t.setConversationId("TestCreatingParticipantByUserClass");
        t.addParticipant("OneUserByUserId");

        User user = User.fromUserId("CreateUserFromId and then add it ");
        t.addParticipant(user);

        // Voice signature format as specified here https://aka.ms/cts/signaturegenservice
        String voice = "1.1, 2.2";

        Participant participant = Participant.from("userIdForParticipant", "en-us", voice);
        t.addParticipant(participant);

        String result = getFirstTranscriberResult(t);
        assertEquals(Settings.WavFile8ChannelsUtterance, result);

        t.close();
        t.close();
    }

    @Test
    public void testRemoveParticipant() throws InterruptedException, ExecutionException, TimeoutException {

        SpeechConfig s = SpeechConfig.fromEndpoint(URI.create(inroomEndpoint), Settings.SpeechSubscriptionKey);
        assertNotNull(s);

        WavFileAudioInputStream ais = new WavFileAudioInputStream(Settings.WavFile8Channels);
        assertNotNull(ais);

        ConversationTranscriber t = new ConversationTranscriber(s, AudioConfig.fromStreamInput(ais));
        assertNotNull(t);
        assertNotNull(t.getTranscriberImpl());
        assertTrue(t instanceof Recognizer);

        t.setConversationId("TestCreatingParticipantByUserClass");
        Boolean exception = false;
        try {
            t.removeParticipant("NoneExist");
        }
        catch (Exception ex) {
            System.out.println("Got Exception in removeParticipant:" + ex.toString());
            exception = true;
        }
        assertEquals(exception, true);

        t.addParticipant("OneUserByUserId");
        t.removeParticipant("OneUserByUserId");

        User user = User.fromUserId("User object created from User.FromUserId");
        t.addParticipant(user);
        t.removeParticipant(user);

        // Voice signature format as specified here https://aka.ms/cts/signaturegenservice
        String voice = "1.1, 2.2";
        Participant participant = Participant.from("userIdForParticipant", "en-us", voice);
        t.addParticipant(participant);
        t.removeParticipant(participant);

        String result = getFirstTranscriberResult(t);
        assertEquals(Settings.WavFile8ChannelsUtterance, result);

        t.close();
        t.close();
    }

    @Test
    public void testStartAndStopConversationTranscribingAsync() throws InterruptedException, ExecutionException, TimeoutException {
        SpeechConfig s = SpeechConfig.fromEndpoint(URI.create(inroomEndpoint), Settings.SpeechSubscriptionKey);
        assertNotNull(s);

        WavFileAudioInputStream ais = new WavFileAudioInputStream(Settings.WavFile8Channels);
        assertNotNull(ais);

        ConversationTranscriber t = new ConversationTranscriber(s, AudioConfig.fromStreamInput(ais));
        assertNotNull(t);
        assertNotNull(t.getTranscriberImpl());
        assertTrue(t instanceof Recognizer);

        t.setConversationId("ConversationPullStreamTest");

        t.addParticipant("xyz@example.com");

        String result = getFirstTranscriberResult(t);
        assertEquals(Settings.WavFile8ChannelsUtterance, result);

        t.close();
        s.close();
    }

    private String getFirstTranscriberResult(ConversationTranscriber t) throws InterruptedException, ExecutionException, TimeoutException {
        String result;
        final ArrayList<String> rEvents = new ArrayList<>();

        t.recognizing.addEventListener((o, e) -> {
            System.out.println("Conversation transcriber recognizing:" + e.toString());
        });

        t.recognized.addEventListener((o, e) -> {
            rEvents.add(e.getResult().getText());
            System.out.println("Conversation transcriber recognized:" + e.toString());
        });

        Future<?> future = t.startTranscribingAsync();
        assertNotNull(future);

        // Wait for max 30 seconds
        future.get(30, TimeUnit.SECONDS);

        assertFalse(future.isCancelled());
        assertTrue(future.isDone());

        // wait until we get at least on final result
        long now = System.currentTimeMillis();
        while(((System.currentTimeMillis() - now) < 30000) &&
                (rEvents.isEmpty())) {
            Thread.sleep(200);
        }

        assertEquals(1, rEvents.size());
        result = rEvents.get(0);

        future = t.stopTranscribingAsync();
        assertNotNull(future);
        
        // Wait for max 30 seconds
        future.get(30, TimeUnit.SECONDS);

        assertFalse(future.isCancelled());
        assertTrue(future.isDone());

        return result;
    }
}