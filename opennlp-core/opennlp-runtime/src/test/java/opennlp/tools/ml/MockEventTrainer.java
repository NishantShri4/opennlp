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

package opennlp.tools.ml;

import java.util.Map;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingConfiguration;
import opennlp.tools.util.TrainingParameters;

public class MockEventTrainer implements EventTrainer<TrainingParameters> {

  public MaxentModel train(ObjectStream<Event> events) {
    return null;
  }

  @Override
  public MaxentModel train(DataIndexer<TrainingParameters> indexer) {
    return null;
  }

  @Override
  public void init(TrainingParameters trainingParams, Map<String, String> reportMap) {
  }

  @Override
  public void init(TrainingParameters trainParams, Map<String, String> reportMap,
                   TrainingConfiguration config) {
  }

}
