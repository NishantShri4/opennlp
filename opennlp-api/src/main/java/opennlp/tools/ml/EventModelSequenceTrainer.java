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

import java.io.IOException;

import opennlp.tools.commons.Trainer;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceStream;
import opennlp.tools.util.Parameters;

/**
 * A specialized {@link Trainer} that is based on a 'EventModelSequence' approach.
 * @param <T> The generic type of elements to process via a {@link SequenceStream}.
 */
public interface EventModelSequenceTrainer<T, P extends Parameters> extends Trainer<P> {

  String SEQUENCE_VALUE = "EventModelSequence";

  /**
   * Trains a {@link MaxentModel} for given {@link SequenceStream<T> events}.
   * 
   * @param events The input {@link SequenceStream<T> events}.
   *               
   * @return The trained {@link MaxentModel}.
   * @throws IOException Thrown if IO errors occurred.
   */
  MaxentModel train(SequenceStream<T> events) throws IOException;

}
