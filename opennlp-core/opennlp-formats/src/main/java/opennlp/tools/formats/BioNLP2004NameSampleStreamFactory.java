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

import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

/**
 * @see BioNLP2004NameSampleStream
 */
public class BioNLP2004NameSampleStreamFactory extends
        AbstractSampleStreamFactory<NameSample, BioNLP2004NameSampleStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
    @ParameterDescription(valueName = "DNA,protein,cell_type,cell_line,RNA")
    String getTypes();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class,
        "bionlp2004", new BioNLP2004NameSampleStreamFactory(Parameters.class));
  }

  protected BioNLP2004NameSampleStreamFactory(Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<NameSample> create(String[] args) {
    Parameters params = validateBasicFormatParameters(args, Parameters.class);

    int typesToGenerate = 0;
    String types = params.getTypes();
    if (types.contains("DNA")) {
      typesToGenerate = typesToGenerate |
          BioNLP2004NameSampleStream.GENERATE_DNA_ENTITIES;
    }
    if (types.contains("protein")) {
      typesToGenerate = typesToGenerate |
          BioNLP2004NameSampleStream.GENERATE_PROTEIN_ENTITIES;
    }
    if (types.contains("cell_type")) {
      typesToGenerate = typesToGenerate |
          BioNLP2004NameSampleStream.GENERATE_CELLTYPE_ENTITIES;
    }
    if (types.contains("cell_line")) {
      typesToGenerate = typesToGenerate |
          BioNLP2004NameSampleStream.GENERATE_CELLLINE_ENTITIES;
    }
    if (types.contains("RNA")) {
      typesToGenerate = typesToGenerate |
          BioNLP2004NameSampleStream.GENERATE_RNA_ENTITIES;
    }

    try {
      return new BioNLP2004NameSampleStream(
          FormatUtil.createInputStreamFactory(params.getData()), typesToGenerate);
    } catch (IOException ex) {
      throw new TerminateToolException(-1,
              "IO Error while creating an Input Stream: " + ex.getMessage(), ex);
    }
  }
}
