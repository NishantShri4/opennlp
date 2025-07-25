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

package opennlp.tools.ml.maxent.io;

import java.io.IOException;
import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.OnePassRealValueDataIndexer;
import opennlp.tools.ml.model.RealValueFileEventStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

public class RealValueFileEventStreamTest {

  private DataIndexer<TrainingParameters> indexer;

  @BeforeEach
  void initIndexer() {
    TrainingParameters trainingParameters = new TrainingParameters();
    trainingParameters.put(Parameters.CUTOFF_PARAM, 1);
    indexer = new OnePassRealValueDataIndexer();
    indexer.init(trainingParameters, new HashMap<>());
  }

  @Test
  void testLastLineBug() throws IOException {
    try (RealValueFileEventStream rvfes = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/io/rvfes-bug-data-ok.txt")) {
      indexer.index(rvfes);
    }
    Assertions.assertEquals(1, indexer.getOutcomeLabels().length);

    try (RealValueFileEventStream rvfes = new RealValueFileEventStream(
        "src/test/resources/data/opennlp/maxent/io/rvfes-bug-data-broken.txt")) {
      indexer.index(rvfes);
    }
    Assertions.assertEquals(1, indexer.getOutcomeLabels().length);
  }
}
