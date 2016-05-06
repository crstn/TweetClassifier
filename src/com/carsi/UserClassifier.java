package com.carsi;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import cc.mallet.classify.Classifier;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.Labeling;

public class UserClassifier {

	public static void main(String[] args) throws Exception {

		int limit = 50;
		
		if(args.length == 1)
			classifyAllUserTweets(args[0], loadClassifier(), limit);
		else
			System.out.println("Run with exactly one argument, the full path to list_of_files.txt");
		
		
		
		
		

	}
	
	public static void classifyAllUserTweets(String file, Classifier classifier, int limit){
		try {
			ArrayList<String> userNames = getUsernames(file);
			
			for (String u : userNames) {
				
				System.out.println("Classifying user " + u);
				
				PrintWriter out = new PrintWriter("tweets-" + u + ".json");
				out.println(classifyUserTimeline(u, classifier, limit));
				out.close();
				
			}
			
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	/**
	 * Generates a list of all unique users from our list_of_files.txt, which lists all json files exported from the geodatabase  
	 * @param file
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static ArrayList<String> getUsernames(String file) throws FileNotFoundException, IOException{
		
		ArrayList<String> users = new ArrayList<String>();
		
		try(BufferedReader br = new BufferedReader(new FileReader(file))) {
		    StringBuilder sb = new StringBuilder();
		    String line = br.readLine();

		    while (line != null) {
		        sb.append(line);
		        sb.append(System.lineSeparator());
		        line = br.readLine();
		    }
		    String everything = sb.toString();
		    String[] names = everything.split("'");
		    
		    for (int i = 0; i < names.length; i++) {
		    	if(names[i].endsWith(".json")){
		    		String[] parts = names[i].split("_");
		    		if(parts.length > 3){
		    			if(!users.contains(parts[3])){
		    				users.add(parts[3]);
		    			}
		    		}
		    		
		    	}
			}
		}
		
		return users;
			
	}

	public static Classifier loadClassifier() throws IOException,
			ClassNotFoundException {

		// The standard way to save classifiers and Mallet data
		// for repeated use is through Java serialization.
		// Here we load a serialized classifier from a file.

		String resource = "tweets.classifier";
		InputStream csv = UserClassifier.class.getResourceAsStream(resource);
		Classifier classifier;
		ObjectInputStream ois = new ObjectInputStream(csv);
		classifier = (Classifier) ois.readObject();
		ois.close();

		return classifier;
	}

	public static String getLabelings(Classifier classifier, String tweet)
			throws IOException {

		// Create a new iterator that will read raw instance data from
		// the lines of a file.
		// Lines should be formatted as:
		//
		// [name] [label] [data ... ]
		//
		// in this case, "label" is ignored.

		CsvIterator reader = new CsvIterator(new StringReader(tweet),
				"(\\w+)\\s+(\\w+)\\s+(.*)", 3, 2, 1); // (data, label, name)
														// field indices

		// Create an iterator that will pass each instance through
		// the same pipe that was used to create the training data
		// for the classifier.
		Iterator<Instance> instanceIter = classifier.getInstancePipe()
				.newIteratorFrom(reader);

		// Classifier.classify() returns a Classification object
		// that includes the instance, the classifier, and the
		// classification results (the labeling). Here we only
		// care about the Labeling.

		String[] labels = { "workschool", "shopping", "otherfam", "recreation",
				"dropoff", "social", "none", "eat", "workother" };
		int counter = 0;

		String responseObj = "{";
		while (instanceIter.hasNext()) {
			try {
				Labeling labeling = classifier.classify(instanceIter.next())
						.getLabeling();
				for (int rank = 0; rank < labeling.numLocations(); rank++) {
					for (int i = 0; i < labels.length; i++) {
						String lbl = labeling.getLabelAtRank(rank).toString();
						if (labels[i].equals(lbl)) {

							counter++;
							responseObj += "\"" + labeling.getLabelAtRank(rank)
									+ "\":" + labeling.getValueAtRank(rank)
									+ "";
							if (counter < labels.length)
								responseObj += ", ";

							responseObj += "\n";
						}
					}
				}
			} catch (IllegalStateException e) {

				// @TODO: Mallet trips over some tweets, no idea why:
				e.printStackTrace();
				responseObj += "}";
				return responseObj;
			}

		}
		responseObj += "}";
		return responseObj;
	}

	public static String classifyUserTimeline(String user,
			Classifier classifier, int limit)
			throws IOException {
		// gets Twitter instance with default credentials
		Twitter twitter = new TwitterFactory().getInstance();
		String responseObject = "{\"user\":\"" + user + "\",\n\"tweets\":[\n";
		try {
			List<Status> statuses;
			Paging page = new Paging(1, limit);
			statuses = twitter.getUserTimeline(user, page);

			for (Status status : statuses) {
				// System.out.println("@" + status.getUser().getScreenName()
				// + " - " + status.getText());

				String tweet = status.getText();
				try {
					responseObject += "{\"" + status.getId()
							+ "\": {\n\"datetime\":\""
							+ status.getCreatedAt().toString() + "\",\n"
							+ "\"text\":\"" + tweet + "\",\n"
							+ "\"classification\":\n"
							+ getLabelings(classifier, tweet) + "}\n},";

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// System.out.println(responseObject);

			}

			responseObject = responseObject.substring(0,
					responseObject.length() - 1);

			System.out.println("Final response object:");
			System.out.println(responseObject + "]}");

		} catch (TwitterException te) {
			te.printStackTrace();
			System.out.println("Failed to get timeline: " + te.getMessage());
			// System.exit(-1);
		}

		return responseObject + "]}";
	}

}
