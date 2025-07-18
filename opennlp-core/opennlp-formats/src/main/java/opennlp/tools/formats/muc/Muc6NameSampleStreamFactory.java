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

package opennlp.tools.formats.muc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.commons.Internal;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToStringSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.ThreadSafeTokenizerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringUtil;

/**
 * @see NameSample
 * @see MucNameSampleStream
 */
@Internal
public class Muc6NameSampleStreamFactory
        extends AbstractSampleStreamFactory<NameSample, Muc6NameSampleStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
    @ParameterDescription(valueName = "modelFile")
    File getTokenizerModel();
  }

  protected Muc6NameSampleStreamFactory() {
    super(Parameters.class);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class, "muc6",
        new Muc6NameSampleStreamFactory());
  }

  @Override
  public ObjectStream<NameSample> create(String[] args) {
    if (args == null) {
      throw new IllegalArgumentException("Passed args must not be null!");
    }
    Parameters params = ArgumentParser.parse(args, Parameters.class);
    if (!params.getData().isDirectory() || !params.getData().exists()) {
      throw new TerminateToolException(-1, "The specified data directory is not valid!");
    }
    try {
      TokenizerModel tokenizerModel = new TokenizerModel(params.getTokenizerModel());
      Tokenizer tokenizer = new ThreadSafeTokenizerME(tokenizerModel);

      ObjectStream<String> mucDocStream = new FileToStringSampleStream(
          new DirectorySampleStream(params.getData(),
              file -> StringUtil.toLowerCase(file.getName()).endsWith(".sgm"), false),
          StandardCharsets.UTF_8);

      return new MucNameSampleStream(tokenizer, mucDocStream);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
              "IO Error while creating an Input Stream: " + e.getMessage(), e);
    }
  }
}
