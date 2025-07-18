/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the {@link SentenceDetectorME} class.
 * <p>
 * Verifies OPENNLP-793 in combination with OPENNLP-570.
 * <p>
 * In this context, well-known known German (de_DE) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-793">OPENNLP-793</a>
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-570">OPENNLP-570</a>
 */
public class SentenceDetectorMEGermanTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};
  private static Dictionary abbreviationDict;
  private SentenceModel sentdetectModel;

  @BeforeAll
  static void loadResources() throws IOException {
    abbreviationDict = loadAbbDictionary(Locale.GERMAN);
  }

  private void prepareResources(boolean useTokenEnd) {
    try {
      SentenceDetectorFactory factory = new SentenceDetectorFactory(
          "deu", useTokenEnd, abbreviationDict, EOS_CHARS);
      sentdetectModel = train(factory, Locale.GERMAN);

      assertAll(() -> assertNotNull(sentdetectModel),
          () -> assertEquals("deu", sentdetectModel.getLanguage()));
    } catch (IOException ex) {
      fail("Couldn't train the SentenceModel using test data. Exception: " + ex.getMessage());
    }
  }

  // Example taken from 'Sentences_DE.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx1() {
    prepareResources(true);

    final String sent1 = "Ein Traum, zu dessen Bildung eine besonders starke Verdichtung beigetragen, " +
        "wird für diese Untersuchung das günstigste Material sein.";
    // Here we have two abbreviations "S. = Seite" and "ff. = folgende (Plural)"
    final String sent2 = "Ich wähle den auf S. 183 ff. mitgeteilten Traum von der botanischen Monographie.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String sampleSentences = sent1 + " " + sent2;
    String[] sents = sentDetect.sentDetect(sampleSentences);
    double[] probs = sentDetect.getSentenceProbabilities();

    assertAll(() -> assertEquals(2, sents.length),
        () -> assertEquals(sent1, sents[0]),
        () -> assertEquals(sent2, sents[1]),
        () -> assertEquals(2, probs.length));
  }

  // Reduced example taken from 'Sentences_DE.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx2() {
    prepareResources(true);

    // Here we have three abbreviations: "S. = Seite", "vgl. = vergleiche", and "f. = folgende (Singular)"
    final String sent1 = "Die farbige Tafel, die ich aufschlage, " +
        "geht (vgl. die Analyse S. 185 f.) auf ein neues Thema ein.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sent1);
    double[] probs = sentDetect.getSentenceProbabilities();

    assertAll(() -> assertEquals(1, sents.length),
        () -> assertEquals(sent1, sents[0]),
        () -> assertEquals(1, probs.length));
  }

  // Modified example deduced from 'Sentences_DE.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx3() {
    prepareResources(true);

    // Here we have two abbreviations "z. B. = zum Beispiel" and "S. = Seite"
    final String sent1 = "Die farbige Tafel, die ich aufschlage, " +
        "geht (z. B. die Analyse S. 185) auf ein neues Thema ein.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sent1);
    double[] probs = sentDetect.getSentenceProbabilities();

    assertAll(() -> assertEquals(1, sents.length),
        () -> assertEquals(sent1, sents[0]),
        () -> assertEquals(1, probs.length));
  }

  @Test
  void testSentDetectWithUseTokenEndFalse() {
    prepareResources(false);

    final String sent1 = "Träume sind eine Verbindung von Gedanken.";
    final String sent2 = "Verschiedene Gedanken sind während der Traumformation aktiv.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    //There is no blank space before start of the second sentence.
    String[] sents = sentDetect.sentDetect(sent1 + sent2);
    double[] probs = sentDetect.getSentenceProbabilities();

    assertAll(() -> assertEquals(2, sents.length),
        () -> assertEquals(sent1, sents[0]),
        () -> assertEquals(sent2, sents[1]),
        () -> assertEquals(2, probs.length));
  }
}
