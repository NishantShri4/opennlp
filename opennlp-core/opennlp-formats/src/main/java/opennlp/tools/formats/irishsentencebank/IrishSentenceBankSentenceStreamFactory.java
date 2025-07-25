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

package opennlp.tools.formats.irishsentencebank;

import java.io.IOException;

import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.commons.Internal;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 *
 * @see SentenceSample
 * @see IrishSentenceBankSentenceStream
 */
@Internal
public class IrishSentenceBankSentenceStreamFactory extends
        AbstractSampleStreamFactory<SentenceSample, IrishSentenceBankSentenceStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class,
        "irishsentencebank", new IrishSentenceBankSentenceStreamFactory(
        IrishSentenceBankSentenceStreamFactory.Parameters.class));
  }

  protected IrishSentenceBankSentenceStreamFactory(Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<SentenceSample> create(String[] args) {
    Parameters params = validateBasicFormatParameters(args, Parameters.class);

    IrishSentenceBankDocument isbDoc = null;
    try {
      isbDoc = IrishSentenceBankDocument.parse(params.getData());
    } catch (IOException ex) {
      throw new TerminateToolException(-1,
              "IO Error while creating an Input Stream: " + ex.getMessage(), ex);
    }

    return new IrishSentenceBankSentenceStream(isbDoc);
  }
}
