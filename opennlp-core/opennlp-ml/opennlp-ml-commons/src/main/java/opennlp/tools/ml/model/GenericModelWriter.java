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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.zip.GZIPOutputStream;

import opennlp.tools.ml.AlgorithmType;

/**
 * An generic {@link AbstractModelWriter} implementation.
 *
 * @see AbstractModelWriter
 */
public class GenericModelWriter extends AbstractModelWriter {

  private AbstractModelWriter delegateWriter;

  /**
   * Initializes a {@link GenericModelWriter} for an {@link AbstractModel}
   * with an associated {@link File} the model shall be written to.
   *
   * @param model The {@link AbstractModel model} to write out.
   * @param file The {@link File} that used to be written to.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public GenericModelWriter(AbstractModel model, File file) throws IOException {
    String filename = file.getName();
    OutputStream os;
    // handle the zipped/not zipped distinction
    if (filename.endsWith(".gz")) {
      os = new GZIPOutputStream(new FileOutputStream(file));
      filename = filename.substring(0, filename.length() - 3);
    } else {
      os = new FileOutputStream(file);
    }

    init(model, new DataOutputStream(os));
  }

  /**
   * Initializes a {@link GenericModelWriter} for an {@link AbstractModel}
   * with an associated {@link DataOutputStream} the model shall be written to.
   *
   * @param model The {@link AbstractModel model} to write out.
   * @param dos The {@link DataOutputStream} that used to be written to.
   */
  public GenericModelWriter(AbstractModel model, DataOutputStream dos) {
    init(model, dos);
  }

  private void init(AbstractModel model, DataOutputStream dos) {
    this.delegateWriter = fromType(model.getModelType(), model, dos);
  }

  private AbstractModelWriter fromType(AlgorithmType type, AbstractModel model, DataOutputStream dos) {
    try {
      final Class<? extends AbstractModelWriter> readerClass
          = (Class<? extends AbstractModelWriter>) Class.forName(type.getWriterClazz());

      return readerClass
          .getDeclaredConstructor(AbstractModel.class, DataOutputStream.class)
          .newInstance(model, dos);

    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Given writer is not available in the classpath!", e);
    } catch (InvocationTargetException | InstantiationException | IllegalAccessException |
             NoSuchMethodException e) {
      throw new RuntimeException("Problem instantiating chosen writer class: " + type.getWriterClazz(), e);
    }
  }

  @Override
  public void close() throws IOException {
    delegateWriter.close();
  }

  @Override
  public void persist() throws IOException {
    delegateWriter.persist();
  }

  @Override
  public void writeDouble(double d) throws IOException {
    delegateWriter.writeDouble(d);
  }

  @Override
  public void writeInt(int i) throws IOException {
    delegateWriter.writeInt(i);
  }

  @Override
  public void writeUTF(String s) throws IOException {
    delegateWriter.writeUTF(s);
  }
}
