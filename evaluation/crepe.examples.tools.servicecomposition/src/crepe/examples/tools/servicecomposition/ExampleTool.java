package crepe.examples.tools.servicecomposition;

/*
 * Before running this file you must follow the following steps:
 * 1. Install rJava - by the following command in R install.packages('rJava')
 * 2. Add "-Djava.library.path=.:/usr/lib/R/site-library/rJava/jri/" into the VM arguments of the running configuration 
 * 3. Create an environmental variable R_HOME which contains the lib of the local R installation (/usr/lib/R)
 * 4. Install all the R libraries which are necessary for running the models (foreach, leaps, earth, DAAG, caret, e1071, rpart, languageR, randomForest, MASS, party)
 */
import java.io.File;
import java.io.IOException;

import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;

import utils.FilesUtils;
import utils.PredictorsTuple;

public class ExampleTool {

	// The R engine which creates an interface between R and Java.
	private Rengine re;
	// Names of predictors variables
	private static String[] predictors = { "ID", "Hops", "Orchestrators", "DevFast", "DevMedium", "DevSlow", "LoadSmall", "LoadMedium", "LoadBig" };
	// Chosen Surrogate Model - Available Models: LR, MARS, CART, RF
	private String model = "LR";
	private int objectives = 3;

	public void setModel(String model) {
		this.model = model;
	}

	/**
	 * Evaluates the fitness function of an individual based on the values of the considered predictor variables.
	 * 
	 * @param values
	 * @return
	 */
	public double[] evaluate(int[] values) {
		loadModel(model);

		// Populate the predictors of an example composition configuration
		PredictorsTuple pred = new PredictorsTuple(predictors, values);

		double[] results = new double[objectives];

		for (int i = 1; i <= objectives; i++)
			System.out.println("Objective " + i + " : " + predictData(pred, i));

		return results;
	}

	/**
	 * Method for loading the approximation model of interest in R. Loading the prediction model is done once.
	 * 
	 */
	public void loadModel(String modelName) {

		re = new Rengine(new String[] { "--vanilla" }, false, null);

		if (!re.waitForR()) {
			System.out.println("Cannot load R");
			return;
		}

		try {
			// Store the R model code into a temporary file
			File tempFileR = FilesUtils.createTempFile(FilesUtils.readFile("resources/build" + modelName + ".R"));
			// Store the training dataset into a temporary file too
			String trainingSet = FilesUtils.readFile("resources/trainingSet.csv");
			File tempFileSet = FilesUtils.createTempFile(trainingSet);
			// Store to a variable on R the path name of the training set
			re.eval(String.format("path <- '%s'", tempFileSet.getAbsolutePath().toString())).asString();

			// Load the R model
			re.eval(String.format("source('%s')", tempFileR.getAbsolutePath()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Method for calculating the QoS values (Delay, Network Latency, Success Ratio, Energy) of a candidate
	 * configurations based on a desired approximation model. The considered approximation models are the following: (a)
	 * Linear Regression, (b) Multivariate Adaptive Regression Splines, (c) Classification and Regression Trees, and (d)
	 * Random Forests.
	 * 
	 * @param pred
	 *            is the input values of the used predictor variables
	 * @param metric
	 *            is the QoS metric to be predicted
	 * 
	 * @return Returns the predicted QoS given a set of predictor variables.
	 */
	private double predictData(PredictorsTuple pred, int metric) {
		// Create a temporary .csv file containing the predictor variables of the configuration
		File csv = FilesUtils.writePredictorsCsv(pred.getNames(), pred.getValues());
		// Let know R about the position of the compary .csv file
		re.eval(String.format("path <- '%s'", csv.getAbsolutePath().toString())).asString();

		// Read the .csv file
		re.eval("newData <- read.csv(file=path,head=T,sep=',')").getContent().toString();
		// Do the necessary data transformation
		re.eval("newData[,2] = log(newData[,2])");

		// Do the actual prediction
		REXP prediction = re.eval("predict(modelQoS" + metric + ", newData[newData$ID == 1,])"); //

		return prediction.asDouble();
	}

	/**
	 * Terminates the R instance.
	 */
	public void shutdownR() {
		re.end();
	}

	public static void main(String[] args) {
		int[] values = { 1, 22, 2, 3, 2, 3, 4, 6, 3 };
		ExampleTool tool = new ExampleTool();
		tool.evaluate(values);
	}
}
