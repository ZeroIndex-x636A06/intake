package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptParserTest {

    private val rollingVtt = """
        WEBVTT
        Kind: captions
        Language: en

        00:00:00.480 --> 00:00:03.000 align:start position:0%
        welcome back to the<00:00:01.200><c> channel</c><00:00:01.800><c> today</c>

        00:00:03.000 --> 00:00:03.010 align:start position:0%
        welcome back to the channel today


        00:00:03.010 --> 00:00:05.500 align:start position:0%
        welcome back to the channel today
        we&#39;re doing a full body &amp; core workout

        00:00:05.500 --> 00:00:05.510 align:start position:0%
        we&#39;re doing a full body &amp; core workout


        00:00:05.510 --> 00:00:08.000 align:start position:0%
        we&#39;re doing a full body &amp; core workout
        [Music]

        00:00:08.000 --> 00:00:10.000
        ♪♪

        00:00:10.000 --> 00:00:12.000
        let&apos;s start with squats
    """.trimIndent()

    @Test
    fun `rolling auto captions keep each line once`() {
        val s = TranscriptParser.parseVtt(rollingVtt)
        assertThat(s.map { it.text }).containsExactly(
            "welcome back to the channel today",
            "we're doing a full body & core workout",
            "let's start with squats",
        ).inOrder()
    }

    @Test
    fun `segments keep their start time and the stretched end`() {
        val s = TranscriptParser.parseVtt(rollingVtt)
        assertThat(s[0].startSec).isWithin(0.001).of(0.48)
        assertThat(s[1].startSec).isWithin(0.001).of(3.01)
        // The repeat cues only stretch the line they repeat.
        assertThat(s[1].endSec).isWithin(0.001).of(8.0)
        assertThat(s[2].startSec).isWithin(0.001).of(10.0)
    }

    @Test
    fun `a cue that extends the previous text keeps only the new words`() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            do ten push ups

            00:00:02.000 --> 00:00:04.000
            do ten push ups now
        """.trimIndent()
        assertThat(TranscriptParser.parseVtt(vtt).map { it.text }).containsExactly("do ten push ups", "now").inOrder()
    }

    @Test
    fun `reads hours and commas in the time`() {
        val vtt = "WEBVTT\n\n01:02:03,500 --> 01:02:05,000\nhello there\n"
        val s = TranscriptParser.parseVtt(vtt).single()
        assertThat(s.startSec).isWithin(0.001).of(3723.5)
        assertThat(s.endSec).isWithin(0.001).of(3725.0)
    }

    @Test
    fun `json3 events become segments`() {
        val json = """
            {"wireMagic":"pb3","events":[
              {"tStartMs":0,"dDurationMs":4000,"segs":[{"utf8":"Let&#39;s start with "},{"utf8":"push ups &amp; planks"}]},
              {"tStartMs":4000,"dDurationMs":1000,"segs":[{"utf8":"\n"}]},
              {"tStartMs":5000,"dDurationMs":3000,"segs":[{"utf8":"[Music]"}]},
              {"tStartMs":8000,"dDurationMs":2500,"segs":[{"utf8":"forty seconds "},{"utf8":"each side"}]},
              {"tStartMs":9000,"dDurationMs":500,"aAppend":1}
            ]}
        """.trimIndent()
        val s = TranscriptParser.parseJson3(json)
        assertThat(s.map { it.text }).containsExactly("Let's start with push ups & planks", "forty seconds each side").inOrder()
        assertThat(s[1].startSec).isWithin(0.001).of(8.0)
        assertThat(s[1].endSec).isWithin(0.001).of(10.5)
    }

    @Test
    fun `json3 line breaks inside an event are dropped when repeated`() {
        val json = """{"events":[
            {"tStartMs":0,"dDurationMs":2000,"segs":[{"utf8":"first line\nsecond line"}]},
            {"tStartMs":2000,"dDurationMs":2000,"segs":[{"utf8":"second line\nthird line"}]}
        ]}"""
        assertThat(TranscriptParser.parseJson3(json).map { it.text }).containsExactly("first line second line", "third line").inOrder()
    }

    @Test
    fun `parse picks the format and survives garbage`() {
        assertThat(TranscriptParser.parse("""{"events":[{"tStartMs":0,"dDurationMs":1000,"segs":[{"utf8":"hi"}]}]}""")).hasSize(1)
        assertThat(TranscriptParser.parse("not a caption file")).isEmpty()
        assertThat(TranscriptParser.parse("{ broken json")).isEmpty()
        assertThat(TranscriptParser.parse("")).isEmpty()
    }

    @Test
    fun `sound cues and music notes are not speech`() {
        assertThat(TranscriptParser.isCue("[Music]")).isTrue()
        assertThat(TranscriptParser.isCue("(applause)")).isTrue()
        assertThat(TranscriptParser.isCue("♪ ♪")).isTrue()
        assertThat(TranscriptParser.isCue("[Intro Music]")).isTrue()
        assertThat(TranscriptParser.isCue("squats [Music] now")).isFalse()
        assertThat(TranscriptParser.clean("squats [Music] now ♪")).isEqualTo("squats now")
        assertThat(TranscriptParser.clean(">> okay go")).isEqualTo("okay go")
    }

    @Test
    fun `html entities are decoded once`() {
        assertThat(TranscriptParser.decodeEntities("a &amp; b &lt;c&gt; &quot;d&quot; &#8217; &#x41;")).isEqualTo("a & b <c> \"d\" ’ A")
        assertThat(TranscriptParser.decodeEntities("&amp;lt;")).isEqualTo("&lt;")
        assertThat(TranscriptParser.decodeEntities("no entity")).isEqualTo("no entity")
        assertThat(TranscriptParser.decodeEntities("&#99999999;")).isEqualTo("")
    }

    @Test
    fun `pasted transcript from the YouTube panel keeps its times`() {
        val pasted = "0:00\nwelcome back\n0:04\ntoday is leg day\nlet's go\n1:05\nfirst up squats\n1:02:10\nthe end"
        val s = TranscriptParser.parsePasted(pasted)
        assertThat(s.map { it.startSec }).containsExactly(0.0, 4.0, 65.0, 3730.0).inOrder()
        assertThat(s[1].text).isEqualTo("today is leg day let's go")
        assertThat(s[0].endSec).isWithin(0.001).of(4.0)
    }

    @Test
    fun `pasted lines that start with a time also work and a typed list is not a transcript`() {
        assertThat(TranscriptParser.parsePasted("0:05 warm up\n0:30 squats\n1:00 lunges").map { it.text }).containsExactly("warm up", "squats", "lunges").inOrder()
        assertThat(TranscriptParser.parsePasted("Squats 3 x 12\nLunges 3 x 10\nPlank 45 sec")).isEmpty()
        assertThat(TranscriptParser.parsePasted("0:05 only one\n0:30 two")).isEmpty()
    }

    @Test
    fun `language choice prefers english then the device language`() {
        assertThat(TranscriptLanguage.requested("de-DE")).containsExactly("en", "de").inOrder()
        assertThat(TranscriptLanguage.requested("en")).containsExactly("en")
        assertThat(TranscriptLanguage.pick(listOf("de", "en-US", "en"), "de")).isEqualTo("en")
        assertThat(TranscriptLanguage.pick(listOf("de", "en-US"), "de")).isEqualTo("en-US")
        assertThat(TranscriptLanguage.pick(listOf("en-orig", "en"), "fr")).isEqualTo("en")
        assertThat(TranscriptLanguage.pick(listOf("de"), "de")).isEqualTo("de")
        assertThat(TranscriptLanguage.pick(listOf("ja", "ko"), "de")).isNull()
        assertThat(TranscriptLanguage.pick(emptyList(), "en")).isNull()
    }

    @Test
    fun `a transcript survives its cache format and bad cache is ignored`() {
        val t = Transcript("dQw4w9WgXcQ", "en", true, listOf(TranscriptSegment(1.5, 3.0, "hello"), TranscriptSegment(3.0, 5.0, "world")))
        assertThat(Transcript.decode(t.encode())).isEqualTo(t)
        assertThat(Transcript.decode("garbage")).isNull()
        assertThat(t.plainText()).isEqualTo("hello\nworld")
    }

    @Test
    fun `timed text joins short lines, carries times and keeps inside its budget`() {
        val segs = (0 until 300).map { TranscriptSegment(it * 2.0, it * 2.0 + 2, "line number $it with some words") }
        val full = Transcript("dQw4w9WgXcQ", "en", false, segs).timedText(1_000_000)
        assertThat(full).startsWith("[0:00] line number 0")
        assertThat(full).contains("[1:00]")
        val small = Transcript("dQw4w9WgXcQ", "en", false, segs).timedText(2_000)
        assertThat(small.length).isAtMost(2_000)
        // An even spread: the end of the video is still represented.
        assertThat(small.lines().last()).contains("line number")
        val lastTime = small.lines().last().substringAfter('[').substringBefore(']')
        assertThat(lastTime.substringBefore(':').toInt()).isAtLeast(5)
    }
}
