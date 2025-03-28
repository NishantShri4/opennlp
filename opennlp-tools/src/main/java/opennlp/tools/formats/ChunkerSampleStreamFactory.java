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

package opennlp.tools.formats;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkSampleStream;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.util.ObjectStream;

/**
 * Factory producing OpenNLP {@link ChunkSampleStream}s.
 */
public class ChunkerSampleStreamFactory extends
        AbstractSampleStreamFactory<ChunkSample, ChunkerSampleStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(ChunkSample.class,
            StreamFactoryRegistry.DEFAULT_FORMAT, new ChunkerSampleStreamFactory(Parameters.class));
  }

  protected ChunkerSampleStreamFactory(Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<ChunkSample> create(String[] args) {
    ObjectStream<String> lineStream = readData(args, Parameters.class);
    return new ChunkSampleStream(lineStream);
  }
}
