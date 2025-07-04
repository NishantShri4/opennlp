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

package opennlp.tools.chunker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * This dummy chunk sample stream reads a file formatted as described at
 * <a href="http://www.cnts.ua.ac.be/conll2000/chunking/output.html/">] and
 * can be used together with DummyChunker simulate a chunker.
 */
public class DummyChunkSampleStream extends
    FilterObjectStream<String, ChunkSample> {

  private static final Logger logger = LoggerFactory.getLogger(DummyChunkSampleStream.class);

  private final boolean mIsPredicted;
  private int count = 0;

  // the predicted flag sets if the stream will contain the expected or the
  // predicted tags.
  public DummyChunkSampleStream(ObjectStream<String> samples, boolean isPredicted) {
    super(samples);
    mIsPredicted = isPredicted;
  }

  /**
   * Returns a pair representing the expected and the predicted at 0: the
   * chunk tag according to the corpus at 1: the chunk tag predicted
   *
   * @see ObjectStream#read()
   */
  public ChunkSample read() throws IOException {

    List<String> toks = new ArrayList<>();
    List<String> posTags = new ArrayList<>();
    List<String> chunkTags = new ArrayList<>();
    List<String> predictedChunkTags = new ArrayList<>();

    for (String line = samples.read(); line != null && !line.isEmpty(); line = samples
        .read()) {
      String[] parts = line.split(" ");
      if (parts.length != 4) {
        logger.warn("Skipping corrupt line {}: {}", count, line);
      } else {
        toks.add(parts[0]);
        posTags.add(parts[1]);
        chunkTags.add(parts[2]);
        predictedChunkTags.add(parts[3]);
      }
      count++;
    }

    if (!toks.isEmpty()) {
      if (mIsPredicted) {
        return new ChunkSample(toks.toArray(new String[0]),
            posTags.toArray(new String[0]),
            predictedChunkTags
            .toArray(new String[0]));
      } else
        return new ChunkSample(toks.toArray(new String[0]),
            posTags.toArray(new String[0]),
            chunkTags.toArray(new String[0]));
    } else {
      return null;
    }

  }

}
