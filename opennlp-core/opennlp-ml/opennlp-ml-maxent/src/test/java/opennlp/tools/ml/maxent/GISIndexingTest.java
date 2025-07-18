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

package opennlp.tools.ml.maxent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.DataIndexerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

public class GISIndexingTest {

  private static final String[][] cntx = new String[][] {
      {"dog", "cat", "mouse"},
      {"text", "print", "mouse"},
      {"dog", "pig", "cat", "mouse"}
  };
  private static final String[] outputs = new String[] {"A", "B", "A"};

  private ObjectStream<Event> createEventStream() {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < cntx.length; i++) {
      events.add(new Event(outputs[i], cntx[i]));
    }
    return ObjectStreamUtils.createObjectStream(events);
  }

  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream) method
   */
  @Test
  void testGISTrainSignature1() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream()) {
      TrainingParameters params = createDefaultTrainingParameters();
      params.put(Parameters.CUTOFF_PARAM, 1);

      EventTrainer<TrainingParameters> trainer = new GISTrainer();
      trainer.init(params, null);

      Assertions.assertNotNull(trainer.train(eventStream));
    }
  }

  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream,boolean smoothing) method
   */
  @Test
  void testGISTrainSignature2() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream()) {
      TrainingParameters params = createDefaultTrainingParameters();
      params.put(Parameters.CUTOFF_PARAM, 1);
      params.put("smoothing", true);
      EventTrainer<TrainingParameters> trainer = new GISTrainer();
      trainer.init(params, null);

      Assertions.assertNotNull(trainer.train(eventStream));
    }
  }

  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream, int iterations, int cutoff) method
   */
  @Test
  void testGISTrainSignature3() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream()) {
      TrainingParameters params = createDefaultTrainingParameters();

      params.put(Parameters.ITERATIONS_PARAM, 10);
      params.put(Parameters.CUTOFF_PARAM, 1);

      EventTrainer<TrainingParameters> trainer = new GISTrainer();
      trainer.init(params, null);

      Assertions.assertNotNull(trainer.train(eventStream));
    }
  }

  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream, int iterations, int cutoff, double sigma) method
   */
  @Test
  void testGISTrainSignature4() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream()) {
      TrainingParameters params = createDefaultTrainingParameters();
      params.put(Parameters.ITERATIONS_PARAM, 10);
      params.put(Parameters.CUTOFF_PARAM, 1);
      GISTrainer trainer = new GISTrainer();
      trainer.init(params, null);
      trainer.setGaussianSigma(0.01);

      Assertions.assertNotNull(trainer.trainModel(eventStream));
    }
  }

  /*
   * Test the GIS.trainModel((ObjectStream<Event> eventStream, int iterations, int cutoff,
   * boolean smoothing, boolean printMessagesWhileTraining)) method
   */
  @Test
  void testGISTrainSignature5() throws IOException {
    try (ObjectStream<Event> eventStream = createEventStream()) {
      TrainingParameters params = createDefaultTrainingParameters();

      params.put(Parameters.ITERATIONS_PARAM, 10);
      params.put(Parameters.CUTOFF_PARAM, 1);
      params.put("smoothing", false);

      EventTrainer<TrainingParameters> trainer = new GISTrainer();
      trainer.init(params, null);
      Assertions.assertNotNull(trainer.train(eventStream));
    }
  }

  @Test
  void testIndexingWithTrainingParameters() throws IOException {
    ObjectStream<Event> eventStream = createEventStream();

    TrainingParameters parameters = TrainingParameters.defaultParams();
    // by default we are using GIS/EventTrainer/Cutoff of 5/100 iterations
    parameters.put(Parameters.ITERATIONS_PARAM, 10);
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE);
    parameters.put(Parameters.CUTOFF_PARAM, 1);
    // note: setting the SORT_PARAM to true is the default, so it is not really needed
    parameters.put(AbstractDataIndexer.SORT_PARAM, true);

    // guarantee that you have a GIS trainer...
    EventTrainer<TrainingParameters> trainer = new GISTrainer();
    trainer.init(parameters, new HashMap<>());
    Assertions.assertEquals("opennlp.tools.ml.maxent.GISTrainer", trainer.getClass().getName());
    AbstractEventTrainer<TrainingParameters> aeTrainer = (AbstractEventTrainer<TrainingParameters>) trainer;
    // guarantee that you have a OnePassDataIndexer ...
    DataIndexer<TrainingParameters> di = aeTrainer.getDataIndexer(eventStream);
    Assertions.assertEquals("opennlp.tools.ml.model.OnePassDataIndexer", di.getClass().getName());
    Assertions.assertEquals(3, di.getNumEvents());
    Assertions.assertEquals(2, di.getOutcomeLabels().length);
    Assertions.assertEquals(6, di.getPredLabels().length);

    // change the parameters and try again...

    eventStream.reset();

    parameters.put(Parameters.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    parameters.put(Parameters.CUTOFF_PARAM, 2);

    trainer = new QNTrainer();
    trainer.init(parameters, new HashMap<>());
    Assertions.assertEquals("opennlp.tools.ml.maxent.quasinewton.QNTrainer", trainer.getClass().getName());
    aeTrainer = (AbstractEventTrainer<TrainingParameters>) trainer;
    di = aeTrainer.getDataIndexer(eventStream);
    Assertions.assertEquals("opennlp.tools.ml.model.TwoPassDataIndexer", di.getClass().getName());

    eventStream.close();
  }

  @Test
  void testIndexingFactory() throws IOException {
    Map<String, String> myReportMap = new HashMap<>();
    ObjectStream<Event> eventStream = createEventStream();

    // set the cutoff to 1 for this test.
    TrainingParameters parameters = new TrainingParameters();
    parameters.put(Parameters.CUTOFF_PARAM, 1);

    // test with a 1 pass data indexer...
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE);
    DataIndexer<TrainingParameters> di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assertions.assertEquals("opennlp.tools.ml.model.OnePassDataIndexer", di.getClass().getName());
    di.index(eventStream);
    Assertions.assertEquals(3, di.getNumEvents());
    Assertions.assertEquals(2, di.getOutcomeLabels().length);
    Assertions.assertEquals(6, di.getPredLabels().length);

    eventStream.reset();

    // test with a 2-pass data indexer...
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assertions.assertEquals("opennlp.tools.ml.model.TwoPassDataIndexer", di.getClass().getName());
    di.index(eventStream);
    Assertions.assertEquals(3, di.getNumEvents());
    Assertions.assertEquals(2, di.getOutcomeLabels().length);
    Assertions.assertEquals(6, di.getPredLabels().length);

    // the rest of the test doesn't actually index, so we can close the eventstream.
    eventStream.close();

    // test with a 1-pass Real value dataIndexer
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_ONE_PASS_REAL_VALUE);
    di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assertions.assertEquals("opennlp.tools.ml.model.OnePassRealValueDataIndexer", di.getClass().getName());


    // test with an UNRegistered MockIndexer
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, "opennlp.tools.ml.maxent.MockDataIndexer");
    di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assertions.assertEquals("opennlp.tools.ml.maxent.MockDataIndexer", di.getClass().getName());
  }

  private static TrainingParameters createDefaultTrainingParameters() {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(Parameters.ALGORITHM_PARAM, Parameters.ALGORITHM_DEFAULT_VALUE);
    mlParams.put(Parameters.ITERATIONS_PARAM, 100);
    mlParams.put(Parameters.CUTOFF_PARAM, 5);

    return mlParams;
  }
}
