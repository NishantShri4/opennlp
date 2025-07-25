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

package opennlp.tools.ml.maxent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.commons.Trainer;
import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.EvalParameters;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.MutableContext;
import opennlp.tools.ml.model.OnePassDataIndexer;
import opennlp.tools.ml.model.Prior;
import opennlp.tools.ml.model.UniformPrior;
import opennlp.tools.monitoring.DefaultTrainingProgressMonitor;
import opennlp.tools.monitoring.LogLikelihoodThresholdBreached;
import opennlp.tools.monitoring.StopCriteria;
import opennlp.tools.monitoring.TrainingMeasure;
import opennlp.tools.monitoring.TrainingProgressMonitor;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingConfiguration;
import opennlp.tools.util.TrainingParameters;

/**
 * An implementation of Generalized Iterative Scaling (GIS).
 * <p>
 * The reference paper for this implementation was Adwait Ratnaparkhi's tech report at the
 * University of Pennsylvania's Institute for Research in Cognitive Science,
 * and is available at <a href ="ftp://ftp.cis.upenn.edu/pub/ircs/tr/97-08.ps.Z">
 *   ftp://ftp.cis.upenn.edu/pub/ircs/tr/97-08.ps.Z</a>.
 * <p>
 * The slack parameter used in the above implementation has been removed by default
 * from the computation and a method for updating with Gaussian smoothing has been
 * added per Investigating GIS and Smoothing for Maximum Entropy Taggers, Clark and Curran (2002).
 * <a href="http://acl.ldc.upenn.edu/E/E03/E03-1071.pdf">http://acl.ldc.upenn.edu/E/E03/E03-1071.pdf</a>.
 * <p>
 * The slack parameter can be used by setting {@code useSlackParameter} to {@code true}.
 * Gaussian smoothing can be used by setting {@code useGaussianSmoothing} to {@code true}.
 * <p>
 * A {@link Prior} can be used to train models which converge to the distribution which minimizes the
 * relative entropy between the distribution specified by the empirical constraints of the training
 * data and the specified prior. By default, the uniform distribution is used as the prior.
 */
public class GISTrainer extends AbstractEventTrainer<TrainingParameters> {

  private static final Logger logger = LoggerFactory.getLogger(GISTrainer.class);

  public static final String LOG_LIKELIHOOD_THRESHOLD_PARAM = "LLThreshold";
  public static final double LOG_LIKELIHOOD_THRESHOLD_DEFAULT = 0.0001;
  private double llThreshold = 0.0001;
  /**
   * Specifies whether unseen context/outcome pairs should be estimated as occur very infrequently.
   */
  private boolean useSimpleSmoothing = false;
  /**
   * Specified whether parameter updates should prefer a distribution of parameters which
   * is gaussian.
   */
  private boolean useGaussianSmoothing = false;
  private double sigma = 2.0;
  // If we are using smoothing, this is used as the "number" of
  // times we want the trainer to imagine that it saw a feature that it
  // actually didn't see.  Defaulted to 0.1.
  private double _smoothingObservation = 0.1;
  /**
   * Number of unique events which occurred in the event set.
   */
  private int numUniqueEvents;
  /**
   * Number of predicates.
   */
  private int numPreds;
  /**
   * Number of outcomes.
   */
  private int numOutcomes;
  /**
   * Records the array of predicates seen in each event.
   */
  private int[][] contexts;
  /**
   * The value associated with each context. If null then context values are assumes to be 1.
   */
  private float[][] values;
  /**
   * List of outcomes for each event i, in context[i].
   */
  private int[] outcomeList;
  /**
   * Records the num of times an event has been seen for each event i, in context[i].
   */
  private int[] numTimesEventsSeen;
  /**
   * Stores the String names of the outcomes. The GIS only tracks outcomes as
   * ints, and so this array is needed to save the model to disk and thereby
   * allow users to know what the outcome was in human understandable terms.
   */
  private String[] outcomeLabels;
  /**
   * Stores the String names of the predicates. The GIS only tracks predicates
   * as ints, and so this array is needed to save the model to disk and thereby
   * allow users to know what the outcome was in human understandable terms.
   */
  private String[] predLabels;
  /**
   * Stores the observed expected values of the features based on training data.
   */
  private MutableContext[] observedExpects;
  /**
   * Stores the estimated parameter value of each predicate during iteration
   */
  private MutableContext[] params;
  /**
   * Stores the expected values of the features based on the current models
   */
  private MutableContext[][] modelExpects;
  /**
   * This is the prior distribution that the model uses for training.
   */
  private Prior prior;
  /**
   * Initial probability for all outcomes.
   */
  private EvalParameters evalParams;

  /**
   * If we are using smoothing, this is used as the "number" of times we want
   * the trainer to imagine that it saw a feature that it actually didn't see.
   * Defaulted to 0.1.
   */
  private static final String SMOOTHING_PARAM = "Smoothing";
  private static final boolean SMOOTHING_DEFAULT = false;
  private static final String SMOOTHING_OBSERVATION_PARAM = "SmoothingObservation";
  private static final double SMOOTHING_OBSERVATION = 0.1;

  private static final String GAUSSIAN_SMOOTHING_PARAM = "GaussianSmoothing";
  private static final boolean GAUSSIAN_SMOOTHING_DEFAULT = false;
  private static final String GAUSSIAN_SMOOTHING_SIGMA_PARAM = "GaussianSmoothingSigma";
  private static final double GAUSSIAN_SMOOTHING_SIGMA_DEFAULT = 2.0;
  
  /**
   * Initializes a {@link GISTrainer}.
   * <p>
   * <b>Note:</b><br>
   * The resulting instance does not print progress messages about training to STDOUT.
   */
  public GISTrainer() {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSortAndMerge() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void init(TrainingParameters trainingParameters, Map<String, String> reportMap) {
    super.init(trainingParameters, reportMap);

    llThreshold = trainingParameters.getDoubleParameter(LOG_LIKELIHOOD_THRESHOLD_PARAM,
        LOG_LIKELIHOOD_THRESHOLD_DEFAULT);

    useSimpleSmoothing = trainingParameters.getBooleanParameter(SMOOTHING_PARAM, SMOOTHING_DEFAULT);
    if (useSimpleSmoothing) {
      _smoothingObservation = 
          trainingParameters.getDoubleParameter(SMOOTHING_OBSERVATION_PARAM, SMOOTHING_OBSERVATION);
    }
    
    useGaussianSmoothing = 
        trainingParameters.getBooleanParameter(GAUSSIAN_SMOOTHING_PARAM, GAUSSIAN_SMOOTHING_DEFAULT);
    if (useGaussianSmoothing) {
      sigma = trainingParameters.getDoubleParameter(
          GAUSSIAN_SMOOTHING_SIGMA_PARAM, GAUSSIAN_SMOOTHING_SIGMA_DEFAULT);
    }
    
    if (useSimpleSmoothing && useGaussianSmoothing) 
      throw new RuntimeException("Cannot set both Gaussian smoothing and Simple smoothing");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MaxentModel doTrain(DataIndexer<TrainingParameters> indexer) throws IOException {
    int iterations = getIterations();

    int threads = trainingParameters.getIntParameter(Parameters.THREADS_PARAM, 1);
    return trainModel(iterations, indexer, threads);
  }

  /**
   * Sets whether this trainer will use smoothing while training the model.
   * <p>
   * <b>Note:</b><br>
   * This can improve model accuracy, though training will potentially take
   * longer and use more memory. Model size will also be larger.
   *
   * @param smooth {@code true} if smoothing is desired, {@code false} if not.
   */
  public void setSmoothing(boolean smooth) {
    useSimpleSmoothing = smooth;
  }

  /**
   * Sets whether this trainer will use smoothing while training the model.
   * <p>
   * <b>Note:</b><br>
   * This can improve model accuracy, though training will potentially take
   * longer and use more memory. Model size will also be larger.
   *
   * @param timesSeen The "number" of times we want the trainer to imagine
   *                  it saw a feature that it actually didn't see
   */
  public void setSmoothingObservation(double timesSeen) {
    _smoothingObservation = timesSeen;
  }

  /**
   * Sets whether this trainer will use smoothing while training the model.
   * <p>
   * <b>Note:</b><br>
   * This can improve model accuracy, though training will potentially take
   * longer and use more memory. Model size will also be larger.
   *
   * @param sigmaValue The Gaussian sigma value used for smoothing.
   */
  public void setGaussianSigma(double sigmaValue) {
    useGaussianSmoothing = true;
    sigma = sigmaValue;
  }

  /**
   * Trains a model using the GIS algorithm, assuming 100 iterations and no
   * cutoff.
   *
   * @param eventStream The {@link ObjectStream eventStream} holding the data
   *                    on which this model will be trained.
   *                    
   * @return A trained {@link GISModel} which can be used immediately or saved to
   *         disk using an {@link opennlp.tools.ml.maxent.io.GISModelWriter}.
   */
  public GISModel trainModel(ObjectStream<Event> eventStream) throws IOException {
    return trainModel(eventStream, 100, 0);
  }

  /**
   * Trains a GIS model on the event in the specified event stream, using the specified number
   * of iterations and the specified count cutoff.
   *
   * @param eventStream A {@link ObjectStream stream} of all events.
   * @param iterations  The number of iterations to use for GIS.
   * @param cutoff      The number of times a feature must occur to be included.
   *
   * @return A trained {@link GISModel} which can be used immediately or saved to
   *         disk using an {@link opennlp.tools.ml.maxent.io.GISModelWriter}.
   */
  public GISModel trainModel(ObjectStream<Event> eventStream, int iterations,
                             int cutoff) throws IOException {
    DataIndexer<TrainingParameters> indexer = new OnePassDataIndexer();
    TrainingParameters indexingParameters = new TrainingParameters();
    indexingParameters.put(Parameters.CUTOFF_PARAM, cutoff);
    indexingParameters.put(Parameters.ITERATIONS_PARAM, iterations);
    Map<String, String> reportMap = new HashMap<>();
    indexer.init(indexingParameters, reportMap);
    indexer.index(eventStream);
    return trainModel(iterations, indexer);
  }

  /**
   * Trains a model using the GIS algorithm.
   *
   * @param iterations The number of GIS iterations to perform.
   * @param di         The {@link DataIndexer} used to compress events in memory.
   *                   
   * @return A trained {@link GISModel} which can be used immediately or saved to
   *         disk using an {@link opennlp.tools.ml.maxent.io.GISModelWriter}.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public GISModel trainModel(int iterations, DataIndexer<TrainingParameters> di) {
    return trainModel(iterations, di, new UniformPrior(), 1);
  }

  /**
   * Trains a model using the GIS algorithm.
   *
   * @param iterations The number of GIS iterations to perform.
   * @param di         The {@link DataIndexer} used to compress events in memory.
   * @param threads    The number of thread to train with. Must be greater than {@code 0}.
   *
   * @return A trained {@link GISModel} which can be used immediately or saved to
   *         disk using an {@link opennlp.tools.ml.maxent.io.GISModelWriter}.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public GISModel trainModel(int iterations, DataIndexer<TrainingParameters> di, int threads) {
    return trainModel(iterations, di, new UniformPrior(), threads);
  }

  /**
   * Trains a model using the GIS algorithm.
   *
   * @param iterations The number of GIS iterations to perform.
   * @param di         The {@link DataIndexer} used to compress events in memory.
   * @param modelPrior The {@link Prior} distribution used to train this model.
   *                   
   * @return A trained {@link GISModel} which can be used immediately or saved to
   *         disk using an {@link opennlp.tools.ml.maxent.io.GISModelWriter}.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public GISModel trainModel(int iterations, DataIndexer<TrainingParameters> di,
                             Prior modelPrior, int threads) {

    if (threads <= 0) {
      throw new IllegalArgumentException("threads must be at least one or greater but is " + threads + "!");
    }

    modelExpects = new MutableContext[threads][];

    /* Incorporate all of the needed info *****/
    logger.info("Incorporating indexed data for training...");
    contexts = di.getContexts();
    values = di.getValues();
    /*
    The number of times a predicate occurred in the training data.
   */
    int[] predicateCounts = di.getPredCounts();
    numTimesEventsSeen = di.getNumTimesEventsSeen();
    numUniqueEvents = contexts.length;
    this.prior = modelPrior;
    //printTable(contexts);

    // determine the correction constant and its inverse
    double correctionConstant = 0;
    for (int ci = 0; ci < contexts.length; ci++) {
      if (values == null || values[ci] == null) {
        if (contexts[ci].length > correctionConstant) {
          correctionConstant = contexts[ci].length;
        }
      } else {
        float cl = values[ci][0];
        for (int vi = 1; vi < values[ci].length; vi++) {
          cl += values[ci][vi];
        }

        if (cl > correctionConstant) {
          correctionConstant = cl;
        }
      }
    }
    logger.info("done.");

    outcomeLabels = di.getOutcomeLabels();
    outcomeList = di.getOutcomeList();
    numOutcomes = outcomeLabels.length;

    predLabels = di.getPredLabels();
    prior.setLabels(outcomeLabels, predLabels);
    numPreds = predLabels.length;

    logger.info("\tNumber of Event Tokens: {} " +
        "\n\t Number of Outcomes: {} " +
        "\n\t Number of Predicates: {}", numUniqueEvents, numOutcomes, numPreds);

    // set up feature arrays
    float[][] predCount = new float[numPreds][numOutcomes];
    for (int ti = 0; ti < numUniqueEvents; ti++) {
      for (int j = 0; j < contexts[ti].length; j++) {
        if (values != null && values[ti] != null) {
          predCount[contexts[ti][j]][outcomeList[ti]] += numTimesEventsSeen[ti] * values[ti][j];
        } else {
          predCount[contexts[ti][j]][outcomeList[ti]] += numTimesEventsSeen[ti];
        }
      }
    }

    // A fake "observation" to cover features which are not detected in
    // the data.  The default is to assume that we observed "1/10th" of a
    // feature during training.
    final double smoothingObservation = _smoothingObservation;

    // Get the observed expectations of the features. Strictly speaking,
    // we should divide the counts by the number of Tokens, but because of
    // the way the model's expectations are approximated in the
    // implementation, this is cancelled out when we compute the next
    // iteration of a parameter, making the extra divisions wasteful.
    params = new MutableContext[numPreds];
    for (int i = 0; i < modelExpects.length; i++) {
      modelExpects[i] = new MutableContext[numPreds];
    }
    observedExpects = new MutableContext[numPreds];

    // The model does need the correction constant and the correction feature. The correction constant
    // is only needed during training, and the correction feature is not necessary.
    // For compatibility reasons the model contains form now on a correction constant of 1,
    // and a correction param 0.
    evalParams = new EvalParameters(params, numOutcomes);
    int[] activeOutcomes = new int[numOutcomes];
    int[] outcomePattern;
    int[] allOutcomesPattern = new int[numOutcomes];
    for (int oi = 0; oi < numOutcomes; oi++) {
      allOutcomesPattern[oi] = oi;
    }
    int numActiveOutcomes;
    for (int pi = 0; pi < numPreds; pi++) {
      numActiveOutcomes = 0;
      if (useSimpleSmoothing) {
        numActiveOutcomes = numOutcomes;
        outcomePattern = allOutcomesPattern;
      } else { //determine active outcomes
        for (int oi = 0; oi < numOutcomes; oi++) {
          if (predCount[pi][oi] > 0) {
            activeOutcomes[numActiveOutcomes] = oi;
            numActiveOutcomes++;
          }
        }
        if (numActiveOutcomes == numOutcomes) {
          outcomePattern = allOutcomesPattern;
        } else {
          outcomePattern = new int[numActiveOutcomes];
          System.arraycopy(activeOutcomes, 0, outcomePattern, 0, numActiveOutcomes);
        }
      }
      params[pi] = new MutableContext(outcomePattern, new double[numActiveOutcomes]);
      for (int i = 0; i < modelExpects.length; i++) {
        modelExpects[i][pi] = new MutableContext(outcomePattern, new double[numActiveOutcomes]);
      }
      observedExpects[pi] = new MutableContext(outcomePattern, new double[numActiveOutcomes]);
      for (int aoi = 0; aoi < numActiveOutcomes; aoi++) {
        int oi = outcomePattern[aoi];
        params[pi].setParameter(aoi, 0.0);
        for (MutableContext[] modelExpect : modelExpects) {
          modelExpect[pi].setParameter(aoi, 0.0);
        }
        if (predCount[pi][oi] > 0) {
          observedExpects[pi].setParameter(aoi, predCount[pi][oi]);
        } else if (useSimpleSmoothing) {
          observedExpects[pi].setParameter(aoi, smoothingObservation);
        }
      }
    }

    logger.info("...done.");

    /* Find the parameters *****/
    if (threads == 1) {
      logger.info("Computing model parameters ...");
    } else {
      logger.info("Computing model parameters in {} threads...", threads);
    }

    findParameters(iterations, correctionConstant);

    // Create and return the model
    return new GISModel(params, predLabels, outcomeLabels);

  }

  /* Estimate and return the model parameters. */
  private void findParameters(int iterations, double correctionConstant) {
    int threads = modelExpects.length;

    ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName("opennlp.tools.ml.maxent.ModelExpectationComputeTask.nextIteration()");
      thread.setDaemon(true);
      return thread;
    });

    CompletionService<ModelExpectationComputeTask> completionService =
        new ExecutorCompletionService<>(executor);
    double prevLL = 0.0;
    double currLL;

    //Get the Training Progress Monitor and the StopCriteria.
    TrainingProgressMonitor progressMonitor = getTrainingProgressMonitor(trainingConfiguration);
    StopCriteria<Double> stopCriteria = getStopCriteria(trainingConfiguration);

    logger.info("Performing {} iterations.", iterations);
    for (int i = 1; i <= iterations; i++) {
      currLL = nextIteration(correctionConstant, completionService, i);
      if (i > 1) {
        if (prevLL > currLL) {
          logger.warn("Model Diverging: loglikelihood decreased");
          break;
        }
        if (stopCriteria.test(currLL - prevLL)) {
          progressMonitor.finishedTraining(iterations, stopCriteria);
          break;
        }
      }
      prevLL = currLL;
    }

    //At this point, all iterations have finished successfully.
    if (!progressMonitor.isTrainingFinished()) {
      progressMonitor.finishedTraining(iterations, null);
    }
    progressMonitor.display(true);

    // kill a bunch of these big objects now that we don't need them
    observedExpects = null;
    modelExpects = null;
    numTimesEventsSeen = null;
    contexts = null;
    executor.shutdown();
  }

  //modeled on implementation in  Zhang Le's maxent kit
  private double gaussianUpdate(int predicate, int oid, double correctionConstant) {
    double param = params[predicate].getParameters()[oid];
    double x0 = 0.0;
    double modelValue = modelExpects[0][predicate].getParameters()[oid];
    double observedValue = observedExpects[predicate].getParameters()[oid];
    for (int i = 0; i < 50; i++) {
      double tmp = modelValue * StrictMath.exp(correctionConstant * x0);
      double f = tmp + (param + x0) / sigma - observedValue;
      double fp = tmp * correctionConstant + 1 / sigma;
      if (fp == 0) {
        break;
      }
      double x = x0 - f / fp;
      if (StrictMath.abs(x - x0) < 0.000001) {
        x0 = x;
        break;
      }
      x0 = x;
    }
    return x0;
  }

  /* Compute one iteration of GIS and return log-likelihood.*/
  private double nextIteration(double correctionConstant,
                               CompletionService<ModelExpectationComputeTask> completionService,
                               int iteration) {
    // compute contribution of p(a|b_i) for each feature and the new
    // correction parameter
    double loglikelihood = 0.0;
    int numEvents = 0;
    int numCorrect = 0;

    // Each thread gets equal number of tasks, if the number of tasks
    // is not divisible by the number of threads, the first "leftOver"
    // threads have one extra task.
    int numberOfThreads = modelExpects.length;
    int taskSize = numUniqueEvents / numberOfThreads;
    int leftOver = numUniqueEvents % numberOfThreads;

    // submit all tasks to the completion service.
    for (int i = 0; i < numberOfThreads; i++) {
      if (i < leftOver) {
        completionService.submit(new ModelExpectationComputeTask(i, i * taskSize + i,
            taskSize + 1));
      } else {
        completionService.submit(new ModelExpectationComputeTask(i,
            i * taskSize + leftOver, taskSize));
      }
    }

    for (int i = 0; i < numberOfThreads; i++) {
      ModelExpectationComputeTask finishedTask;
      try {
        finishedTask = completionService.take().get();
      } catch (InterruptedException e) {
        // TODO: We got interrupted, but that is currently not really supported!
        // For now we fail hard. We hopefully soon
        // handle this case properly!
        throw new IllegalStateException("Interruption is not supported!", e);
      } catch (ExecutionException e) {
        // Only runtime exception can be thrown during training, if one was thrown
        // it should be re-thrown. That could for example be a NullPointerException
        // which is caused through a bug in our implementation.
        throw new RuntimeException("Exception during training: " + e.getMessage(), e);
      }

      // When they are done, retrieve the results ...
      numEvents += finishedTask.getNumEvents();
      numCorrect += finishedTask.getNumCorrect();
      loglikelihood += finishedTask.getLoglikelihood();
    }

    // merge the results of the two computations
    for (int pi = 0; pi < numPreds; pi++) {
      int[] activeOutcomes = params[pi].getOutcomes();

      for (int aoi = 0; aoi < activeOutcomes.length; aoi++) {
        for (int i = 1; i < modelExpects.length; i++) {
          modelExpects[0][pi].updateParameter(aoi, modelExpects[i][pi].getParameters()[aoi]);
        }
      }
    }

    // compute the new parameter values
    for (int pi = 0; pi < numPreds; pi++) {
      double[] observed = observedExpects[pi].getParameters();
      double[] model = modelExpects[0][pi].getParameters();
      int[] activeOutcomes = params[pi].getOutcomes();
      for (int aoi = 0; aoi < activeOutcomes.length; aoi++) {
        if (useGaussianSmoothing) {
          params[pi].updateParameter(aoi, gaussianUpdate(pi, aoi, correctionConstant));
        } else {
          if (model[aoi] == 0) {
            logger.warn("Model expects == 0 for {} {}", predLabels[pi], outcomeLabels[aoi]);
          }
          //params[pi].updateParameter(aoi,(StrictMath.log(observed[aoi]) - StrictMath.log(model[aoi])));
          params[pi].updateParameter(aoi, ((StrictMath.log(observed[aoi]) - StrictMath.log(model[aoi]))
              / correctionConstant));
        }

        for (MutableContext[] modelExpect : modelExpects) {
          modelExpect[pi].setParameter(aoi, 0.0); // re-initialize to 0.0's
        }

      }
    }

    getTrainingProgressMonitor(trainingConfiguration).
        finishedIteration(iteration, numCorrect, numEvents, TrainingMeasure.LOG_LIKELIHOOD, loglikelihood);

    return loglikelihood;
  }

  private class ModelExpectationComputeTask implements Callable<ModelExpectationComputeTask> {

    private final int startIndex;
    private final int length;
    final private int threadIndex;
    private double loglikelihood = 0;
    private int numEvents = 0;
    private int numCorrect = 0;

    // startIndex to compute, number of events to compute
    ModelExpectationComputeTask(int threadIndex, int startIndex, int length) {
      this.startIndex = startIndex;
      this.length = length;
      this.threadIndex = threadIndex;
    }

    public ModelExpectationComputeTask call() {

      final double[] modelDistribution = new double[numOutcomes];


      for (int ei = startIndex; ei < startIndex + length; ei++) {

        // TODO: check interruption status here, if interrupted set a poisoned flag and return

        if (values != null) {
          prior.logPrior(modelDistribution, contexts[ei], values[ei]);
          GISModel.eval(contexts[ei], values[ei], modelDistribution, evalParams);
        } else {
          prior.logPrior(modelDistribution, contexts[ei]);
          GISModel.eval(contexts[ei], modelDistribution, evalParams);
        }
        for (int j = 0; j < contexts[ei].length; j++) {
          int pi = contexts[ei][j];
          int[] activeOutcomes = modelExpects[threadIndex][pi].getOutcomes();
          for (int aoi = 0; aoi < activeOutcomes.length; aoi++) {
            int oi = activeOutcomes[aoi];

            // numTimesEventsSeen must also be thread safe
            if (values != null && values[ei] != null) {
              modelExpects[threadIndex][pi].updateParameter(aoi, modelDistribution[oi]
                  * values[ei][j] * numTimesEventsSeen[ei]);
            } else {
              modelExpects[threadIndex][pi].updateParameter(aoi, modelDistribution[oi]
                  * numTimesEventsSeen[ei]);
            }
          }
        }

        loglikelihood += StrictMath.log(modelDistribution[outcomeList[ei]]) * numTimesEventsSeen[ei];

        numEvents += numTimesEventsSeen[ei];

        int max = ArrayMath.argmax(modelDistribution);
        if (max == outcomeList[ei]) {
          numCorrect += numTimesEventsSeen[ei];
        }

      }

      return this;
    }

    synchronized int getNumEvents() {
      return numEvents;
    }

    synchronized int getNumCorrect() {
      return numCorrect;
    }

    synchronized double getLoglikelihood() {
      return loglikelihood;
    }
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
        ? trainingConfig.stopCriteria() : new LogLikelihoodThresholdBreached(trainingParameters);
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
    return trainingConfig != null && trainingConfig.progMon() != null ?
        trainingConfig.progMon() : new DefaultTrainingProgressMonitor();
  }

}
