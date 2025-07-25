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

package opennlp.tools.util.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.BinaryFileDataReader;
import opennlp.tools.ml.model.GenericModelReader;
import opennlp.tools.ml.model.GenericModelWriter;

/**
 * An {@link ArtifactSerializer} implementation for {@link AbstractModel models}.
 */
public class GenericModelSerializer implements ArtifactSerializer<AbstractModel> {

  @Override
  public AbstractModel create(InputStream in) throws IOException {
    return new GenericModelReader(new BinaryFileDataReader(in)).getModel();
  }

  @Override
  public void serialize(AbstractModel artifact, OutputStream out) throws IOException {
    Objects.requireNonNull(artifact, "model parameter must not be null");
    Objects.requireNonNull(out, "out parameter must not be null");

    GenericModelWriter modelWriter = new GenericModelWriter(artifact,
            new DataOutputStream(new OutputStream() {
              @Override
              public void write(int b) throws IOException {
                out.write(b);
              }
            }));

    modelWriter.persist();
  }

  /**
   * Registers a new {@link GenericModelSerializer} in the given {@code factories} mapping.
   *
   * @param factories A {@link Map} holding {@link ArtifactSerializer} for re-use.
   */
  public static void register(Map<String, ArtifactSerializer<?>> factories) {
    factories.put("model", new GenericModelSerializer());
  }
}
