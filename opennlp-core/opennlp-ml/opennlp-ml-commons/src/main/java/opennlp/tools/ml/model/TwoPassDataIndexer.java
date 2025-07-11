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

package opennlp.tools.ml.model;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * Collecting event and context counts by making two passes over the events.
 * <p>
 * The first pass determines which contexts will be used by the model, and the
 * second pass creates the events in memory containing only the contexts which
 * will be used. This greatly reduces the amount of memory required for storing
 * the events. During the first pass a temporary event file is created which
 * is read during the second pass.
 *
 * @see DataIndexer
 * @see AbstractDataIndexer
 */
public class TwoPassDataIndexer extends AbstractDataIndexer<TrainingParameters> {

  private static final Logger logger = LoggerFactory.getLogger(TwoPassDataIndexer.class);

  public TwoPassDataIndexer() {}

  /**
   * {@inheritDoc}
   */
  @Override
  public void index(ObjectStream<Event> eventStream) throws IOException {
    int cutoff = trainingParameters.getIntParameter(Parameters.CUTOFF_PARAM,
        Parameters.CUTOFF_DEFAULT_VALUE);
    boolean sort = trainingParameters.getBooleanParameter(SORT_PARAM, SORT_DEFAULT);

    logger.info("Indexing events with TwoPass using cutoff of {}", cutoff);
    logger.info("Computing event counts...");

    long start = System.currentTimeMillis();
    Map<String,Integer> predicateIndex = new HashMap<>();
    File tmp = Files.createTempFile("events", null).toFile();
    tmp.deleteOnExit();
    int numEvents;
    long writeChecksum;

    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp));
        CheckedOutputStream writeStream = new CheckedOutputStream(out, new CRC32C());
        DataOutputStream dos = new DataOutputStream(writeStream)) {

      numEvents = computeEventCounts(eventStream, dos, predicateIndex, cutoff);
      writeChecksum = writeStream.getChecksum().getValue();
      logger.info("done. {} events", numEvents);
    }

    List<ComparableEvent> eventsToCompare;
    long readChecksum;
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(tmp));
         CheckedInputStream readStream = new CheckedInputStream(in, new CRC32C());
         EventStream readEventsStream = new EventStream(new DataInputStream(readStream))) {
      logger.info("Indexing...");
      eventsToCompare = index(readEventsStream, predicateIndex);
      readChecksum = readStream.getChecksum().getValue();
    }
    tmp.delete();

    if (readChecksum != writeChecksum) {
      throw new IOException("Checksum for writing and reading events did not match.");
    } else {
      logger.info("done.");

      if (sort) {
        logger.info("Sorting and merging events... ");
      }
      else {
        logger.info("Collecting events... ");
      }
      sortAndMerge(eventsToCompare,sort);
      logger.info(String.format("Done indexing in %.2f s.", (System.currentTimeMillis() - start) / 1000d));
    }
  }

  /**
   * Reads events from <tt>eventStream</tt> into a linked list.  The
   * predicates associated with each event are counted and any which
   * occur at least <tt>cutoff</tt> times are added to the
   * <tt>predicatesInOut</tt> map along with a unique integer index.
   * <p>
   * Protocol:
   *  1 - (utf string) - Event outcome
   *  2 - (int) - Event context array length
   *  3+ - (utf string) - Event context string
   *  4 - (int) - Event values array length
   *  5+ - (float) - Event value
   *
   * @param eventStream an <code>EventStream</code> value
   * @param eventStore a writer to which the events are written to for later processing.
   * @param predicatesInOut a <code>TObjectIntHashMap</code> value
   * @param cutoff an <code>int</code> value
   */
  private int computeEventCounts(ObjectStream<Event> eventStream, DataOutputStream eventStore,
      Map<String,Integer> predicatesInOut, int cutoff) throws IOException {
    Map<String,Integer> counter = new HashMap<>();
    int eventCount = 0;

    Event ev;
    while ((ev = eventStream.read()) != null) {
      eventCount++;

      eventStore.writeUTF(ev.getOutcome());

      eventStore.writeInt(ev.getContext().length);
      String[] ec = ev.getContext();
      update(ec, counter);
      for (String ctxString : ec)
        eventStore.writeUTF(ctxString);

      if (ev.getValues() == null) {
        eventStore.writeInt(0);
      }
      else {
        eventStore.writeInt(ev.getValues().length);
        for (float value : ev.getValues())
          eventStore.writeFloat(value);
      }
    }

    String[] predicateSet = counter.entrySet().stream()
        .filter(entry -> entry.getValue() >= cutoff)
        .map(Map.Entry::getKey).sorted()
        .toArray(String[]::new);

    predCounts = new int[predicateSet.length];
    for (int i = 0; i < predicateSet.length; i++) {
      predCounts[i] = counter.get(predicateSet[i]);
      predicatesInOut.put(predicateSet[i], i);
    }

    return eventCount;
  }

  private static class EventStream implements ObjectStream<Event> {

    private final DataInputStream inputStream;

    public EventStream(DataInputStream dataInputStream) {
      this.inputStream = dataInputStream;
    }

    @Override
    public Event read() throws IOException {
      if (inputStream.available() != 0) {
        String outcome = inputStream.readUTF();
        int contextLength = inputStream.readInt();
        String[] context = new String[contextLength];
        for (int i = 0; i < contextLength; i++)
          context[i] = inputStream.readUTF();
        int valuesLength = inputStream.readInt();
        float[] values = null;
        if (valuesLength > 0) {
          values = new float[valuesLength];
          for (int i = 0; i < valuesLength; i++)
            values[i] = inputStream.readFloat();
        }
        return new Event(outcome, context, values);
      }
      else {
        return null;
      }
    }

    @Override
    public void reset() throws IOException, UnsupportedOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
      inputStream.close();
    }
  }
}
