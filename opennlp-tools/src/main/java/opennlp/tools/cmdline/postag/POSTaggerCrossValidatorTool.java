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

package opennlp.tools.cmdline.postag;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractCrossValidatorTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.params.FineGrainedEvaluatorParams;
import opennlp.tools.cmdline.postag.POSTaggerCrossValidatorTool.CVToolParams;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerCrossValidator;
import opennlp.tools.postag.POSTaggerEvaluationMonitor;
import opennlp.tools.util.model.ModelUtil;

public final class POSTaggerCrossValidatorTool
    extends AbstractCrossValidatorTool<POSSample, CVToolParams> {

  interface CVToolParams extends CVParams, TrainingParams, FineGrainedEvaluatorParams {
  }

  private static final Logger logger = LoggerFactory.getLogger(POSTaggerCrossValidatorTool.class);

  public POSTaggerCrossValidatorTool() {
    super(POSSample.class, CVToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "K-fold cross validator for the learnable POS tagger";
  }

  @Override
  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    POSTaggerEvaluationMonitor missclassifiedListener = null;
    if (params.getMisclassified()) {
      missclassifiedListener = new POSEvaluationErrorListener();
    }

    POSTaggerFineGrainedReportListener reportListener = null;
    File reportFile = params.getReportOutputFile();
    OutputStream reportOutputStream = null;
    if (reportFile != null) {
      CmdLineUtil.checkOutputFile("Report Output File", reportFile);
      try {
        reportOutputStream = new FileOutputStream(reportFile);
        reportListener = new POSTaggerFineGrainedReportListener(
            reportOutputStream);
      } catch (FileNotFoundException e) {
        throw createTerminationIOException(e);
      }
    }

    Map<String, Object> resources;
    try {
      resources = TokenNameFinderTrainerTool.loadResources(params.getResources(), params.getFeaturegen());
    }
    catch (IOException e) {
      throw new TerminateToolException(-1,"IO error while loading resources", e);
    }

    byte[] featureGeneratorBytes =
        TokenNameFinderTrainerTool.openFeatureGeneratorBytes(params.getFeaturegen());

    POSTaggerCrossValidator validator;
    try {
      validator = new POSTaggerCrossValidator(params.getLang(), mlParams,
          params.getDict(), featureGeneratorBytes, resources, params.getTagDictCutoff(),
          params.getFactory(), missclassifiedListener, reportListener);

      validator.evaluate(sampleStream, params.getFolds());
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
          + e.getMessage(), e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    logger.info("done");

    if (reportListener != null) {
      logger.info("Writing fine-grained report to {}",
          params.getReportOutputFile().getAbsolutePath());
      reportListener.writeReport();

      try {
        reportOutputStream.flush();
        reportOutputStream.close();
      } catch (IOException e) {
        // nothing to do
      }
    }

    logger.info("Accuracy: {}", validator.getWordAccuracy());
  }
}
