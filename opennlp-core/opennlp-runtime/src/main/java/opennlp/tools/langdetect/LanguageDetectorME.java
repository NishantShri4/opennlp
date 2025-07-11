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

package opennlp.tools.langdetect;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.MutableInt;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Implements a learnable {@link LanguageDetector}.
 *
 * <p>
 * This will process the entire string when called with
 * {@link #predictLanguage(CharSequence)} or
 * {@link #predictLanguages(CharSequence)}.
 * </p>
 * <p>
 * If you want this to stop early, use {@link #probingPredictLanguages(CharSequence)}
 * or {@link #probingPredictLanguages(CharSequence, LanguageDetectorConfig)}.
 * When run in probing mode, this starts at the beginning of the char sequence
 * and runs language detection on chunks of text.  If the end of the
 * string is reached or there are {@link LanguageDetectorConfig#getMinConsecImprovements()}
 * consecutive predictions for the best language and the confidence
 * increases over those last predictions and if the difference
 * in confidence between the highest confidence language
 * and the second highest confidence language is greater than
 * {@link LanguageDetectorConfig#getMinDiff()}, the language detector will
 * stop and report the results.
 * </p>
 * <p>
 * The authors wish to thank Ken Krugler and
 * <a href="https://github.com/kkrugler/yalder">Yalder</a>}
 * for the inspiration for many of the design components of this detector.
 *
 */
public class LanguageDetectorME implements LanguageDetector {

  protected final LanguageDetectorModel model;
  private final LanguageDetectorContextGenerator mContextGenerator;

  /**
   * Initializes an instance with a specific {@link LanguageDetectorModel}.
   * Default feature generation is used.
   *
   * @param model the {@link LanguageDetectorModel} to be used.
   */
  public LanguageDetectorME(LanguageDetectorModel model) {
    this.model = model;
    this.mContextGenerator = model.getFactory().getContextGenerator();
  }

  @Override
  public Language[] predictLanguages(CharSequence content) {
    return predict(arrayToCounts(mContextGenerator.getContext(content)));
  }
  
  @Override
  public Language predictLanguage(CharSequence content) {
    return predictLanguages(content)[0];
  }

  @Override
  public String[] getSupportedLanguages() {
    int numberLanguages = model.getMaxentModel().getNumOutcomes();
    String[] languages = new String[numberLanguages];
    for (int i = 0; i < numberLanguages; i++) {
      languages[i] = model.getMaxentModel().getOutcome(i);
    }
    return languages;
  }

  /**
   * This will stop processing early if the stopping criteria
   * specified in {@link LanguageDetectorConfig#DEFAULT_LANGUAGE_DETECTOR_CONFIG}
   * are met.
   *
   * @param content content to be processed
   * @return A computed {@link ProbingLanguageDetectionResult}.
   */
  public ProbingLanguageDetectionResult probingPredictLanguages(CharSequence content) {
    return probingPredictLanguages(content,
            LanguageDetectorConfig.DEFAULT_LANGUAGE_DETECTOR_CONFIG);
  }

  /**
   * This will stop processing early if the stopping criteria
   * specified in {@link LanguageDetectorConfig#DEFAULT_LANGUAGE_DETECTOR_CONFIG}
   * are met.
   *
   * @param content The textual content to process.
   * @param config The {@link LanguageDetectorConfig} to customize detection.
   *
   * @return A computed {@link ProbingLanguageDetectionResult}.
   */
  public ProbingLanguageDetectionResult probingPredictLanguages(CharSequence content,
                                                                LanguageDetectorConfig config) {
    //list of the languages that received the highest
    //confidence over the last n chunk detections
    List<Language[]> predictions = new LinkedList<>();
    int start = 0;//where to start the next chunk in codepoints
    Language[] currPredictions = null;
    //cache ngram counts across chunks
    Map<CharSequence, MutableInt> ngramCounts = new HashMap<>();
    while (true) {
      int actualChunkSize =
              (start + config.getChunkSize() > config.getMaxLength()) ?
                      config.getMaxLength() - start : config.getChunkSize();
      StringCPLengthPair chunk = chunk(content, start, actualChunkSize);

      if (chunk.length() == 0) {
        if (currPredictions == null) {
          return new ProbingLanguageDetectionResult(predict(ngramCounts), start);
        } else {
          return new ProbingLanguageDetectionResult(currPredictions, start);
        }
      }
      start += chunk.length();
      updateCounts(mContextGenerator.getContext(chunk.s), ngramCounts);
      currPredictions = predict(ngramCounts);
      if (seenEnough(predictions, currPredictions, ngramCounts, config)) {
        return new ProbingLanguageDetectionResult(currPredictions, start);
      }
    }
  }

  private void updateCounts(CharSequence[] context, Map<CharSequence, MutableInt> ngrams) {
    for (CharSequence ngram : context) {
      MutableInt i = ngrams.get(ngram);
      if (i == null) {
        i = new MutableInt(1);
        ngrams.put(ngram, i);
      } else {
        i.increment();
      }
    }
  }

  private Map<CharSequence, MutableInt> arrayToCounts(CharSequence[] context) {
    Map<CharSequence, MutableInt> ngrams = new HashMap<>();
    updateCounts(context, ngrams);
    return ngrams;
  }

  private Language[] predict(Map<CharSequence, MutableInt> ngramCounts) {
    String[] allGrams = new String[ngramCounts.size()];
    float[] counts = new float[ngramCounts.size()];
    int i = 0;
    for (Map.Entry<CharSequence, MutableInt> e : ngramCounts.entrySet()) {
      allGrams[i] = e.getKey().toString();
      // TODO -- once OPENNLP-1261 is fixed,
      // change this to e.getValue().getValue().
      counts[i] = 1;
      i++;
    }
    double[] eval = model.getMaxentModel().eval(allGrams, counts);
    Language[] arr = new Language[eval.length];
    for (int j = 0; j < eval.length; j++) {
      arr[j] = new Language(model.getMaxentModel().getOutcome(j), eval[j]);
    }

    Arrays.sort(arr, (o1, o2) -> Double.compare(o2.getConfidence(), o1.getConfidence()));
    return arr;
  }

  /**
   * Overriding this for different behavior to determine if there is enough
   * confidence in the predictions to stop.
   *
   * @param predictionsQueue queue of earlier predictions
   * @param newPredictions most recent predictions
   * @param ngramCounts -- not currently used, but might be useful
   * @return {@code true} if enough text has been processed to make a determination,
   *         else {@code false}.
   */
  boolean seenEnough(List<Language[]> predictionsQueue, Language[] newPredictions,
                     Map<CharSequence, MutableInt> ngramCounts, LanguageDetectorConfig config) {

    if (predictionsQueue.size() < config.getMinConsecImprovements()) {
      predictionsQueue.add(newPredictions);
      return false;
    } else if (predictionsQueue.size() > config.getMinConsecImprovements()
            && predictionsQueue.size() > 0) {
      predictionsQueue.remove(0);
    }
    predictionsQueue.add(newPredictions);
    if (config.getMinDiff() > 0.0 &&
            newPredictions[0].getConfidence() -
                    newPredictions[1].getConfidence() < config.getMinDiff()) {
      return false;
    }
    String lastLang = null;
    double lastConf = -1.0;
    //iterate through the last predictions
    //and check that the lang with the highest confidence
    //hasn't changed, and that the confidence in it
    //hasn't decreased
    for (Language[] predictions : predictionsQueue) {
      if (lastLang == null) {
        lastLang = predictions[0].getLang();
        lastConf = predictions[0].getConfidence();
        continue;
      } else {
        if (!lastLang.equals(predictions[0].getLang())) {
          return false;
        }
        if (lastConf > predictions[0].getConfidence()) {
          return false;
        }
      }
      lastLang = predictions[0].getLang();
      lastConf = predictions[0].getConfidence();
    }
    return true;
  }

  private StringCPLengthPair chunk(CharSequence content, int start, int chunkSize) {
    if (start == 0 && chunkSize > content.length()) {
      String s = content.toString();
      int codePointLength = s.codePointCount(0, s.length());
      return
              new StringCPLengthPair(s, codePointLength);
    }
    int[] codepoints = content.codePoints().skip(start).limit(chunkSize).toArray();
    return
            new StringCPLengthPair(
                    new String(codepoints, 0, codepoints.length),
                    codepoints.length);
  }

  /**
   * Starts a training of a {@link LanguageDetectorModel} with the given parameters.
   *
   * @param samples The {@link ObjectStream} of {@link LanguageSample} used as input for training.
   * @param mlParams The {@link TrainingParameters} for the context of the training.
   * @param factory The {@link LanguageDetectorFactory} for creating related objects defined
   *                via {@code mlParams}.
   *
   * @return A valid, trained {@link LanguageDetectorModel} instance.
   * @throws IOException Thrown if IO errors occurred.
   */
  public static LanguageDetectorModel train(ObjectStream<LanguageSample> samples,
                                            TrainingParameters mlParams,
                                            LanguageDetectorFactory factory)
      throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<>();

    mlParams.putIfAbsent(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE);

    EventTrainer<TrainingParameters> trainer = TrainerFactory.getEventTrainer(mlParams, manifestInfoEntries);
    MaxentModel model = trainer.train(
        new LanguageDetectorEventStream(samples, factory.getContextGenerator()));

    return new LanguageDetectorModel(model, manifestInfoEntries, factory);
  }

  private record StringCPLengthPair(String s, int length) {

    String getString() {
      return s;
    }
  }
}
