package edu.drexel.psal.jstylo.generics;

import java.util.Map;

import edu.drexel.psal.jstylo.analyzers.WekaAnalyzer;
import edu.drexel.psal.jstylo.analyzers.WriteprintsAnalyzer;
import edu.drexel.psal.jstylo.generics.Logger.LogOut;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

/**
 * 
 * JStylo SimpleAPI Version 1.0<br>
 * 
 * A simple API for the inner JStylo functionality.<br>
 * Provides four constructors at the moment (eventually more) <br>
 * After the SimpleAPI is constructed, users need only call prepareInstances(),
 * (sometimes, depending on constructor) prepareAnalyzer(), and run().<br>
 * After fetch the relevant information with the correct get method<br>
 * @author Travis Dutko
 */

public class SimpleAPI {

	/**
	 * Builder for the SimpleAPI class. <br>
	 *
	 * You must specify at least one of each of the following pairs:<br>
	 * psXMLPath and probSet<br>
	 * cfdXMLPath and cfd<br>
	 * classifierPath  and classifier<br>
	 * 
	 * All other parameters have the following default values:<br>
	 * numThreads = 4<br>
	 * numFolds = 10<br>
	 * type = analysisType.CROSS_VALIDATION<br>
	 * useDocTitles = false<br>
	 * isSparse = true<br>
	 */
	public static class Builder{
		private String psXMLPath;
		private ProblemSet probSet;
		private String cfdXMLPath;
		private CumulativeFeatureDriver cfd;
		private String classifierPath;
		private Classifier classifier;
		private int numThreads = 4;
		private int numFolds = 10;
		private analysisType type = analysisType.CROSS_VALIDATION;
		private boolean useDocTitles = false;
		private boolean isSparse = true;
		private boolean loadDocContents = false;
		
		public Builder(){
			
		}
		
		public Builder psPath(String psXML){
			psXMLPath = psXML;
			return this;
		}
		
		public Builder cfdPath(String cfdXML){
			cfdXMLPath = cfdXML;
			return this;
		}
		
		public Builder classifierPath(String cPath){
			classifierPath = cPath;
			return this;
		}
		
		public Builder ps(ProblemSet ps){
			probSet = ps;
			return this;
		}
		
		public Builder cfd(CumulativeFeatureDriver cFeatDriver){
			cfd = cFeatDriver;
			return this;
		}
		
		public Builder classifier(Classifier classi){
			classifier = classi;
			return this;
		}
		
		public Builder numThreads(int nt){
			numThreads = nt;
			return this;
		}
		
		public Builder numFolds(int nf){
			numFolds = nf;
			return this;
		}
		
		public Builder analysisType(analysisType at){
			type = at;
			return this;
		}
		
		public Builder useDocTitles(boolean udt){
			useDocTitles = udt;
			return this;
		}
		
		public Builder isSparse(boolean is){
			isSparse = is;
			return this;
		}
		
		public Builder loadDocContents(boolean ldc){
			loadDocContents = ldc;
			return this;
		}
		
		public SimpleAPI build(){
			return new SimpleAPI(this);
		}
		
	}
	
	///////////////////////////////// Data
	
	//which evaluation to perform enumeration
	public static enum analysisType {CROSS_VALIDATION,TRAIN_TEST_UNKNOWN,TRAIN_TEST_KNOWN};
	
	//Persistant/necessary data
	InstancesBuilder ib; //does the feature extraction
	String classifierPath; //creates analyzer
	Analyzer analysisDriver; //does the train/test/crossVal
	analysisType selected; //type of evaluation
	int numFolds; //folds for cross val (defaults to 10)
	
	//Result Data
	Map<String,Map<String, Double>> trainTestResults;
	Evaluation resultsEvaluation;
	
	///////////////////////////////// Constructor
	/**
	 * Constructor; the SimpleAPI can be built solely via a Builder.
	 * @param b the builder
	 */
	private SimpleAPI(Builder b){
		ib = new InstancesBuilder();
		
		if (b.psXMLPath==null)
			ib.setProblemSet(b.probSet);
		else 
			ib.setProblemSet(new ProblemSet(b.psXMLPath,b.loadDocContents));
		
		if (b.cfdXMLPath==null)
			ib.setCumulativeFeatureDriver(b.cfd);
		else {
			try {
				ib.setCumulativeFeatureDriver(new CumulativeFeatureDriver(b.cfdXMLPath));
			} catch (Exception e) {
				Logger.logln("Failed to build cfd from xml path: "+b.cfdXMLPath,LogOut.STDERR);
				e.printStackTrace();
			}
		}
		
		if (b.classifier!=null){
			analysisDriver = new WekaAnalyzer(b.classifier);
		} else if (b.classifierPath==null){
			classifierPath = b.classifierPath;
		}
		
		ib.setUseDocTitles(b.useDocTitles);
		ib.setLoadDocContents(b.loadDocContents);
		ib.setNumThreads(b.numThreads);
		selected = b.type;
		numFolds = b.numFolds;
		classifierPath = b.classifierPath;
		
	}
	
	///////////////////////////////// Methods
	
	/**
	 * Prepares the instances objects (stored within the InstancesBuilder)
	 */
	public void prepareInstances() {

		try {
			ib.extractEventsThreaded(); //extracts events from documents
			ib.initializeRelevantEvents(); //creates the List<EventSet> to pay attention to
			ib.initializeAttributes(); //creates the attribute list to base the Instances on
			ib.createTrainingInstancesThreaded(); //creates train Instances
			ib.createTestInstancesThreaded(); //creates test Instances (if present)
			
		} catch (Exception e) {
			System.out.println("Failed to prepare instances");
			e.printStackTrace();
		}

	}

	/**
	 * Prepares the analyzer for classification.<br>
	 * Only use this if you are not passing in a pre-built classifier!
	 */
	public void prepareAnalyzer() {
		try {
			Object tmpObject = null;
			tmpObject = Class.forName(classifierPath).newInstance(); //creates the object from the string

			if (tmpObject instanceof Classifier) { //if it's a weka classifier
				analysisDriver = new WekaAnalyzer(Class.forName(classifierPath) //make a wekaAnalyzer
						.newInstance());
			} else if (tmpObject instanceof WriteprintsAnalyzer) { //otherwise it's a writeprints analyzer
				analysisDriver = new WriteprintsAnalyzer(); 
			}
		} catch (Exception e) {
			System.out.println("Failed to prepare Analyzer");
			e.printStackTrace();
		}
	}
	
	/**
	 * Calculates and stores the infoGain for future use
	 */
	public void calcInfoGain(){
		try {
			ib.calculateInfoGain(); //delegate to underlying Instances Builder
		} catch (Exception e) {
			Logger.logln("Failed to calculate infoGain",LogOut.STDERR);
			e.printStackTrace();
		} 
	}
	
	/**
	 * Applies infoGain to the training and testing instances
	 * @param n the number of features/attributes to keep
	 */
	public void applyInfoGain(int n){
		try {
			ib.applyInfoGain(n);
		} catch (Exception e) {
			System.out.println("Failed to apply infoGain");
			e.printStackTrace();
		}
	}
	
	/**
	 * Perform the actual analysis
	 */
	public void run(){
		
		//switch based on the enum
		switch (selected) {
	
		//do a cross val
		case CROSS_VALIDATION:
			resultsEvaluation = analysisDriver.runCrossValidation(ib.getTrainingInstances(), numFolds, 0);
			break;

		// do a train/test
		case TRAIN_TEST_UNKNOWN:
			trainTestResults = analysisDriver.classify(ib.getTrainingInstances(), ib.getTestInstances(), ib.getProblemSet().getAllTestDocs());
			break;

		//do a train/test where we know the answer and just want statistics
		case TRAIN_TEST_KNOWN:
			ib.getProblemSet().removeAuthor("_Unknown_");
			try {
				Instances train = ib.getTrainingInstances();
				Instances test = ib.getTestInstances();
				test.setClassIndex(test.numAttributes()-1);
				train.setClassIndex(train.numAttributes()-1);
				resultsEvaluation = analysisDriver.getTrainTestEval(train,test);
			} catch (Exception e) {
				Logger.logln("Failed to build trainTest Evaluation");
				e.printStackTrace();
			}
			break;
		
		//should not occur
		default:
			System.out.println("Unreachable. Something went wrong somewhere.");
			break;
		}
	}
	
	///////////////////////////////// Setters/Getters
	
	/**
	 * @param useDocTitles boolean value. Whether or not to set 1st attribute to the document title
	 */
	public void setUseDocTitles(boolean useDocTitles){
		ib.setUseDocTitles(useDocTitles);
	}
	
	/**
	 * @param sparse boolean value. Whether or not to use sparse instances
	 */
	public void setUseSparse(boolean sparse){
		ib.setUseSparse(sparse);
	}
	
	/**
	 * Sets the training Instances object
	 * @param insts the Instances object to use as training data
	 */
	public void setTrainingInstances(Instances insts){
		ib.setTrainingInstances(insts);
	}
	
	/**
	 * Sets the testing Instances object
	 * @param insts the Instances object to use as testing data
	 */
	public void setTestingInstances(Instances insts){
		ib.setTestingInstances(insts);
	}
	
	/**
	 * Sets the type of experiment to run
	 * @param type enum value of either CROSS_VALIDATION, TRAIN_TEST_UNKNOWN, or TRAIN_TEST_KNOWN
	 */
	public void setExperimentType(analysisType type){
		selected = type;
	}
	
	/**
	 * Change the number of folds to use in cross validation
	 * @param n number of folds to use from now on
	 */
	public void setNumFolds(int n){
		numFolds = n;
	}
	
	/**
	 * Sets the number of calculation threads to use
	 * @param nt the number of calculation threads to use
	 */
	public void setNumThreads(int nt){
		ib.setNumThreads(nt);
	}
	
	/**
	 * @return the Instances object describing the training documents
	 */
	public Instances getTrainingInstances(){
		return ib.getTrainingInstances();
	}
	
	/**
	 * @return the Instances object describing the test documents
	 */
	public Instances getTestInstances(){
		return ib.getTestInstances();
	}
	
	/**
	 * @return the infoGain data (not in human readable form lists indices and usefulness)
	 */
	public double[][] getInfoGain(){
		return ib.getInfoGain();
	}
	
	/**
	 * @return the problem set being evaluated
	 */
	public ProblemSet getProblemSet(){
		return ib.getProblemSet();
	}
	
	/**
	 * @return the InstancesBuilder which is responsible for the feature extraction
	 */
	public InstancesBuilder getUnderlyingInstancesBuilder(){
		return ib;
	}
	
	/**
	 * Returns a string of features, in order of most to least useful, with their infogain values<br>
	 * @param showZeroes whether or not to show features that have a 0 as their infoGain value
	 * @return the string representing the infoGain
	 */
	public String getReadableInfoGain(boolean showZeroes){
		
		//initialize the string and infoGain
		String infoString = ">-----InfoGain information: \n\n";
		Instances trainingInstances = ib.getTrainingInstances();
		double[][] infoGain = ib.getInfoGain();
		
		for (int i = 0; i<infoGain.length; i++){
			if (!showZeroes && (infoGain[i][0]==0))
				break;
			
			//match the index to the name and add it to the string
			infoString+=String.format("> %-50s   %f\n",
					trainingInstances.attribute((int)infoGain[i][1]).name(),
					infoGain[i][0]);
		}
		
		//return the result
		return infoString;
	}
	
	/**
	 * @return Map containing train/test results
	 */
	public Map<String,Map<String, Double>> getTrainTestResults(){
		return trainTestResults;
	}
	
	/**
	 * @return the evaluation object containing the classification data
	 */
	public Evaluation getEvaluation(){
		return resultsEvaluation;
	}
	
	/**
	 * @return String containing accuracy, metrics, and confusion matrix from the evaluation
	 */
	public String getStatString() {
		
		try {
			Evaluation eval = getEvaluation();
			String resultsString = "";
			resultsString += eval.toSummaryString(false) + "\n";
			resultsString += eval.toClassDetailsString() + "\n";
			resultsString += eval.toMatrixString() + "\n";
			return resultsString;
		
		} catch (Exception e) {
			System.out.println("Failed to get statistics string");
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @return The accuracy of the given test in percentage format
	 */
	public String getClassificationAccuracy(){
		String results = "";
		Evaluation eval = getEvaluation();
		results+= String.format("%.4f",eval.weightedTruePositiveRate()*100);
		
		return results;
	}
	
	/**
	 * @return the weka classifier being used by the analyzer. Will break something if you try to call it on a non-weka analyzer
	 */
	public Classifier getUnderlyingClassifier(){
		return analysisDriver.getClassifier();
	}
	
	/**
	 * Write an Instances object to a particular file as an arff
	 * @param path where to save the file
	 * @param insts the instances object to be saved
	 */
	public static void writeArff(String path, Instances insts){
		InstancesBuilder.writeToARFF(path,insts);
	}
	
	///////////////////////////////// Main method for testing purposes
	
	public static void main(String[] args){

		SimpleAPI test = new SimpleAPI.Builder().cfdPath("./jsan_resources/feature_sets/writeprints_feature_set_limited.xml")
				.psPath("C:/Users/Mordio/Documents/GitHub/jstylo/jsan_resources/problem_sets/enron_presentation.xml").classifierPath("weka.classifiers.functions.SMO")
				.numThreads(8).analysisType(analysisType.CROSS_VALIDATION).useDocTitles(false).build();

		test.prepareInstances();
		//test.calcInfoGain();
		//test.applyInfoGain(1500);
		test.prepareAnalyzer();
		test.run();
		System.out.println(test.getClassificationAccuracy());
		//System.out.println(test.getStatString());
		
	}
}