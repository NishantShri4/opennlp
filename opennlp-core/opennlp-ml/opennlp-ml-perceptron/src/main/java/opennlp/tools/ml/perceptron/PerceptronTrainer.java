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

package opennlp.tools.ml.perceptron;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.commons.Trainer;
import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.EvalParameters;
import opennlp.tools.ml.model.MutableContext;
import opennlp.tools.monitoring.DefaultTrainingProgressMonitor;
import opennlp.tools.monitoring.IterDeltaAccuracyUnderTolerance;
import opennlp.tools.monitoring.StopCriteria;
import opennlp.tools.monitoring.TrainingMeasure;
import opennlp.tools.monitoring.TrainingProgressMonitor;
import opennlp.tools.util.TrainingConfiguration;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains {@link PerceptronModel models} using the perceptron algorithm.
 * <p>
 * Each outcome is represented as a binary perceptron classifier.
 * This supports standard (integer) weighting as well average weighting as described in:
 * <p>
 * Discriminative Training Methods for Hidden Markov Models: Theory and Experiments
 * with the Perceptron Algorithm. Michael Collins, EMNLP 2002.
 *
 * @see PerceptronModel
 * @see AbstractEventTrainer
 */
public class PerceptronTrainer extends AbstractEventTrainer<TrainingParameters> {

  private static final Logger logger = LoggerFactory.getLogger(PerceptronTrainer.class);

  public static final String PERCEPTRON_VALUE = "PERCEPTRON";
  public static final double TOLERANCE_DEFAULT = .00001;

  /** Number of unique events which occurred in the event set. */
  private int numUniqueEvents;
  /** Number of events in the event set. */
  private int numEvents;

  /** Number of predicates. */
  private int numPreds;
  /** Number of outcomes. */
  private int numOutcomes;
  /** Records the array of predicates seen in each event. */
  private int[][] contexts;

  /** The value associates with each context. If null then context values are assumes to be 1. */
  private float[][] values;

  /** List of outcomes for each event i, in context[i]. */
  private int[] outcomeList;

  /** Records the num of times an event has been seen for each event i, in context[i]. */
  private int[] numTimesEventsSeen;

  /** Stores the String names of the outcomes. The GIS only tracks outcomes
  as ints, and so this array is needed to save the model to disk and
  thereby allow users to know what the outcome was in human
  understandable terms. */
  private String[] outcomeLabels;

  /** Stores the String names of the predicates. The GIS only tracks
  predicates as ints, and so this array is needed to save the model to
  disk and thereby allow users to know what the outcome was in human
  understandable terms. */
  private String[] predLabels;

  private double tolerance = TOLERANCE_DEFAULT;

  private Double stepSizeDecrease;

  private boolean useSkippedlAveraging;

  /**
   * Instantiates a {@link PerceptronTrainer} with default training parameters.
   */
  public PerceptronTrainer() {
  }

  /**
   * Instantiates a {@link PerceptronTrainer} with specific
   * {@link TrainingParameters}.
   *
   * @param parameters The {@link TrainingParameters parameter} to use.
   */
  public PerceptronTrainer(TrainingParameters parameters) {
    super(parameters);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if the algorithm name is not equal to
   *                                  {@link #PERCEPTRON_VALUE}.
   */
  @Override
  public void validate() {
    super.validate();

    String algorithmName = getAlgorithm();
    if (algorithmName != null) {
      if (!PERCEPTRON_VALUE.equals(algorithmName)) {
        throw new IllegalArgumentException("algorithmName must be PERCEPTRON");
      }
    }
  }

  @Override
  public boolean isSortAndMerge() {
    return false;
  }

  @Override
  public AbstractModel doTrain(DataIndexer<TrainingParameters> indexer) throws IOException {
    int iterations = getIterations();
    int cutoff = getCutoff();

    boolean useAverage = trainingParameters.getBooleanParameter("UseAverage", true);
    boolean useSkippedAveraging = trainingParameters.getBooleanParameter("UseSkippedAveraging", false);

    // overwrite otherwise it might not work
    if (useSkippedAveraging)
      useAverage = true;

    double stepSizeDecrease = trainingParameters.getDoubleParameter("StepSizeDecrease", 0);
    double tolerance = trainingParameters.getDoubleParameter("Tolerance",
        PerceptronTrainer.TOLERANCE_DEFAULT);

    this.setSkippedAveraging(useSkippedAveraging);

    if (stepSizeDecrease > 0)
      this.setStepSizeDecrease(stepSizeDecrease);

    this.setTolerance(tolerance);

    return this.trainModel(iterations, indexer, cutoff, useAverage);
  }

  // << members related to AbstractEventTrainer

  /**
   * Specifies the tolerance. If the change in training set accuracy
   * is less than this, stop iterating.
   *
   * @param tolerance The level of tolerance.
   *                  Must not be negative.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public void setTolerance(double tolerance) {

    if (tolerance < 0) {
      throw new
          IllegalArgumentException("tolerance must be a positive number but is " + tolerance + "!");
    }

    this.tolerance = tolerance;
  }

  /**
   * Enables and sets step size decrease. The step size is
   * decreased every iteration by the specified value.
   *
   * @param decrease The step size decrease in percent.
   *                 Must not be negative.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public void setStepSizeDecrease(double decrease) {

    if (decrease < 0 || decrease > 100) {
      throw new
          IllegalArgumentException("decrease must be between 0 and 100 but is " + decrease + "!");
    }

    stepSizeDecrease = decrease;
  }

  /**
   * Enables skipped averaging, this flag changes the standard
   * averaging to special averaging instead.
   * <p>
   * If we are doing averaging, and the current iteration is one
   * of the first 20, or if it is a perfect square, then updated the
   * summed parameters.
   * <p>
   * The reason we don't take all of them is that the parameters change
   * less toward the end of training, so they drown out the contributions
   * of the more volatile early iterations. The use of perfect
   * squares allows us to sample from successively farther apart iterations.
   *
   * @param averaging Whether to skip 'averaging', or not.
   */
  public void setSkippedAveraging(boolean averaging) {
    useSkippedlAveraging = averaging;
  }

  /**
   * Trains a {@link PerceptronModel} with given parameters.
   * 
   * @param iterations The number of iterations to use for training.
   * @param di The {@link DataIndexer} used as data input.
   * @param cutoff The {@link TrainingParameters#CUTOFF_PARAM} value to use for training.
   *               
   * @return A valid, trained {@link AbstractModel perceptron model}.
   */
  public AbstractModel trainModel(int iterations, DataIndexer<TrainingParameters> di,
                                  int cutoff) {
    return trainModel(iterations,di,cutoff,true);
  }

  /**
   * Trains a {@link PerceptronModel} with given parameters.
   *
   * @param iterations The number of iterations to use for training.
   * @param di The {@link DataIndexer} used as data input.
   * @param cutoff The {@link TrainingParameters#CUTOFF_PARAM} value to use for training.
   * @param useAverage Whether to use 'averaging', or not.
   *                   See {@link #setSkippedAveraging(boolean)} for details.
   *
   * @return A valid, trained {@link AbstractModel perceptron model}.
   */
  public AbstractModel trainModel(int iterations, DataIndexer<TrainingParameters> di,
                                  int cutoff, boolean useAverage) {
    logger.info("Incorporating indexed data for training... ");
    contexts = di.getContexts();
    values = di.getValues();
    numTimesEventsSeen = di.getNumTimesEventsSeen();
    numEvents = di.getNumEvents();
    numUniqueEvents = contexts.length;

    outcomeLabels = di.getOutcomeLabels();
    outcomeList = di.getOutcomeList();

    predLabels = di.getPredLabels();
    numPreds = predLabels.length;
    numOutcomes = outcomeLabels.length;

    logger.info("done.");

    logger.info("\tNumber of Event Tokens: {} " +
        "\n\t Number of Outcomes: {} " +
        "\n\t Number of Predicates: {}", numUniqueEvents, numOutcomes, numPreds);

    logger.info("Computing model parameters...");

    MutableContext[] finalParameters = findParameters(iterations, useAverage);

    logger.info("...done.");

    /* Create and return the model *************/
    return new PerceptronModel(finalParameters, predLabels, outcomeLabels);
  }

  private MutableContext[] findParameters(int iterations, boolean useAverage) {

    logger.info("Performing {} iterations.", iterations);

    int[] allOutcomesPattern = new int[numOutcomes];
    for (int oi = 0; oi < numOutcomes; oi++)
      allOutcomesPattern[oi] = oi;

    /* Stores the estimated parameter value of each predicate during iteration. */
    MutableContext[] params = new MutableContext[numPreds];
    for (int pi = 0; pi < numPreds; pi++) {
      params[pi] = new MutableContext(allOutcomesPattern,new double[numOutcomes]);
      for (int aoi = 0; aoi < numOutcomes; aoi++)
        params[pi].setParameter(aoi, 0.0);
    }

    EvalParameters evalParams = new EvalParameters(params, numOutcomes);

    /* Stores the sum of parameter values of each predicate over many iterations. */
    MutableContext[] summedParams = new MutableContext[numPreds];
    if (useAverage) {
      for (int pi = 0; pi < numPreds; pi++) {
        summedParams[pi] = new MutableContext(allOutcomesPattern,new double[numOutcomes]);
        for (int aoi = 0; aoi < numOutcomes; aoi++)
          summedParams[pi].setParameter(aoi, 0.0);
      }
    }

    //Get the Training Progress Monitor and the StopCriteria.
    TrainingProgressMonitor progressMonitor = getTrainingProgressMonitor(trainingConfiguration);
    StopCriteria<Double> stopCriteria = getStopCriteria(trainingConfiguration);

    // Keep track of the previous three accuracies. The difference of
    // the mean of these and the current training set accuracy is used
    // with tolerance to decide whether to stop.
    double prevAccuracy1 = 0.0;
    double prevAccuracy2 = 0.0;
    double prevAccuracy3 = 0.0;

    // A counter for the denominator for averaging.
    int numTimesSummed = 0;

    double stepsize = 1;
    for (int i = 1; i <= iterations; i++) {

      // Decrease the stepsize by a small amount.
      if (stepSizeDecrease != null)
        stepsize *= 1 - stepSizeDecrease;

      int numCorrect = 0;

      for (int ei = 0; ei < numUniqueEvents; ei++) {
        int targetOutcome = outcomeList[ei];

        for (int ni = 0; ni < this.numTimesEventsSeen[ei]; ni++) {

          // Compute the model's prediction according to the current parameters.
          double[] modelDistribution = new double[numOutcomes];
          if (values != null)
            PerceptronModel.eval(contexts[ei], values[ei], modelDistribution, evalParams, false);
          else
            PerceptronModel.eval(contexts[ei], null, modelDistribution, evalParams, false);

          int maxOutcome = ArrayMath.argmax(modelDistribution);

          // If the predicted outcome is different from the target
          // outcome, do the standard update: boost the parameters
          // associated with the target and reduce those associated
          // with the incorrect predicted outcome.
          if (maxOutcome != targetOutcome) {
            for (int ci = 0; ci < contexts[ei].length; ci++) {
              int pi = contexts[ei][ci];
              if (values == null) {
                params[pi].updateParameter(targetOutcome, stepsize);
                params[pi].updateParameter(maxOutcome, -stepsize);
              } else {
                params[pi].updateParameter(targetOutcome, stepsize * values[ei][ci]);
                params[pi].updateParameter(maxOutcome, -stepsize * values[ei][ci]);
              }
            }
          }

          // Update the counts for accuracy.
          if (maxOutcome == targetOutcome)
            numCorrect++;
        }
      }

      // Calculate the training accuracy.
      double trainingAccuracy = (double) numCorrect / numEvents;
      if (i < 10 || (i % 10) == 0) {
        progressMonitor.finishedIteration(i, numCorrect, numEvents,
            TrainingMeasure.ACCURACY, trainingAccuracy);
      }

      // TODO: Make averaging configurable !!!

      boolean doAveraging;

      doAveraging = useAverage && useSkippedlAveraging && (i < 20 || isPerfectSquare(i)) || useAverage;

      if (doAveraging) {
        numTimesSummed++;
        for (int pi = 0; pi < numPreds; pi++)
          for (int aoi = 0; aoi < numOutcomes; aoi++)
            summedParams[pi].updateParameter(aoi, params[pi].getParameters()[aoi]);
      }

      // If the tolerance is greater than the difference between the
      // current training accuracy and all of the previous three
      // training accuracies, stop training.
      if (stopCriteria.test(prevAccuracy1 - trainingAccuracy)
          && stopCriteria.test(prevAccuracy2 - trainingAccuracy)
          && stopCriteria.test(prevAccuracy3 - trainingAccuracy)) {
        progressMonitor.finishedTraining(iterations, stopCriteria);
        break;
      }

      // Update the previous training accuracies.
      prevAccuracy1 = prevAccuracy2;
      prevAccuracy2 = prevAccuracy3;
      prevAccuracy3 = trainingAccuracy;
    }

    //At this point, all iterations have finished successfully.
    if (!progressMonitor.isTrainingFinished()) {
      progressMonitor.finishedTraining(iterations, null);
    }
    progressMonitor.display(true);

    // Output the final training stats.
    trainingStats(evalParams);

    // Create averaged parameters
    if (useAverage) {
      for (int pi = 0; pi < numPreds; pi++)
        for (int aoi = 0; aoi < numOutcomes; aoi++)
          summedParams[pi].setParameter(aoi, summedParams[pi].getParameters()[aoi] / numTimesSummed);

      return summedParams;

    } else {

      return params;

    }

  }

  private double trainingStats(EvalParameters evalParams) {
    int numCorrect = 0;

    for (int ei = 0; ei < numUniqueEvents; ei++) {
      for (int ni = 0; ni < this.numTimesEventsSeen[ei]; ni++) {

        double[] modelDistribution = new double[numOutcomes];

        if (values != null)
          PerceptronModel.eval(contexts[ei], values[ei], modelDistribution, evalParams,false);
        else
          PerceptronModel.eval(contexts[ei], null, modelDistribution, evalParams, false);

        int max = ArrayMath.argmax(modelDistribution);
        if (max == outcomeList[ei])
          numCorrect++;
      }
    }
    double trainingAccuracy = (double) numCorrect / numEvents;
    logger.info("Stats: ({}/{}) {}", numCorrect, numEvents, trainingAccuracy);
    return trainingAccuracy;
  }

  // See whether a number is a perfect square.
  // Inefficient, but fine for our purposes.
  private static boolean isPerfectSquare(int n) {
    int root = (int) StrictMath.sqrt(n);
    return root * root == n;
  }

  /**
   * Get the {@link StopCriteria} associated with this {@link Trainer}.
   *
   * @param trainingConfig {@link TrainingConfiguration}
   * @return {@link StopCriteria}. If {@link TrainingConfiguration} is {@code null} or
   * {@link TrainingConfiguration#stopCriteria()} is {@code null},
   * then return the default {@link StopCriteria}.
   */
  private StopCriteria<Double> getStopCriteria(TrainingConfiguration trainingConfig) {
    return trainingConfig != null && trainingConfig.stopCriteria() != null
        ? trainingConfig.stopCriteria() : new IterDeltaAccuracyUnderTolerance(trainingParameters);
  }

  /**
   * Get the {@link TrainingProgressMonitor} associated with this {@link Trainer}.
   *
   * @param trainingConfig {@link TrainingConfiguration}.
   * @return {@link TrainingProgressMonitor}. If {@link TrainingConfiguration} is {@code null} or
   * {@link TrainingConfiguration#progMon()} is {@code null},
   * then return the default {@link TrainingProgressMonitor}.
   */
  private TrainingProgressMonitor getTrainingProgressMonitor(TrainingConfiguration trainingConfig) {
    return trainingConfig != null && trainingConfig.progMon() != null ? trainingConfig.progMon() :
        new DefaultTrainingProgressMonitor();
  }

}
