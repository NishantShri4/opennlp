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
import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.FileEventStream;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;
import opennlp.tools.ml.model.RealValueFileEventStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

public class RealValueModelTest {

  private DataIndexer<TrainingParameters> testDataIndexer;

  @BeforeEach
  void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(Parameters.CUTOFF_PARAM, 1);
    testDataIndexer = new OnePassRealValueDataIndexer();
    testDataIndexer.init(trainingParameters, new HashMap<>());
  }

  @Test
  void testRealValuedWeightsVsRepeatWeighting() throws IOException {
    GISModel realModel;
    GISTrainer gisTrainer = new GISTrainer();
    try (RealValueFileEventStream rvfes1 = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt")) {
      testDataIndexer.index(rvfes1);
      realModel = gisTrainer.trainModel(100, testDataIndexer);
    }

    GISModel repeatModel;
    try (FileEventStream rvfes2 = new FileEventStream(
        "src/test/resources/data/opennlp/maxent/repeat-weighting-training-data.txt")) {
      testDataIndexer.index(rvfes2);
      repeatModel = gisTrainer.trainModel(100, testDataIndexer);
    }

    String[] features2Classify = new String[] {"feature2", "feature5"};
    double[] realResults = realModel.eval(features2Classify);
    double[] repeatResults = repeatModel.eval(features2Classify);

    Assertions.assertEquals(realResults.length, repeatResults.length);
    for (int i = 0; i < realResults.length; i++) {
      Assertions.assertEquals(repeatResults[i], realResults[i], 0.01f);
    }

    features2Classify = new String[] {"feature1", "feature2", "feature3", "feature4", "feature5"};
    realResults = realModel.eval(features2Classify, new float[] {5.5f, 6.1f, 9.1f, 4.0f, 1.8f});
    repeatResults = repeatModel.eval(features2Classify, new float[] {5.5f, 6.1f, 9.1f, 4.0f, 1.8f});

    Assertions.assertEquals(realResults.length, repeatResults.length);
    for (int i = 0; i < realResults.length; i++) {
      Assertions.assertEquals(repeatResults[i],  realResults[i],0.01f);
    }

  }
}
