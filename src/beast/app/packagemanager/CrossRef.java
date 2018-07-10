package beast.app.packagemanager;

import beast.core.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Walter Xie
 */
public class CrossRef {

    public final String doi;
    protected final URL url;

    public CrossRef(String doi) throws MalformedURLException {
        // rm / in the 1st char and last char
        if (doi.startsWith("/")) doi = doi.substring(1);
//        if (doi.endsWith("/")) doi = doi.substring(0, doi.length()-1);

        this.doi = doi;
        url = new URL("https://api.crossref.org/works/" + doi);

        Log.info("Requesting " + url.toString());
    }

    /**
     * request CrossRef API
     *
     * @return a JSON result
     * @throws Exception
     */
    public String get() throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);

        StringBuilder sb = new StringBuilder();
        String inputLine;
        while ((inputLine = br.readLine()) != null) {
            sb.append(inputLine).append("\n");
        }

        br.close();
        Thread.sleep(1000); // be nice to server
        return sb.toString();
    }

    /**
     * a list of authors in a string, using APA format:
     * surename, firstname (one space after ,)
     *
     * @param result
     * @param printOriginal
     * @return
     * @throws JSONException
     */
    public List<String> parseAuthors(String result, boolean printOriginal) throws JSONException {
        JSONObject json = new JSONObject(result);

        if (printOriginal)
            Log.info(json.toString(4));

        JSONObject msg = json.getJSONObject("message");
        JSONArray arr = msg.getJSONArray("author");
        List<String> authors = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String firstname = arr.getJSONObject(i).getString("given");
            String surename = arr.getJSONObject(i).getString("family");
//            Log.info(firstname + " " + surename);

            // APA format: surename, firstname (one space after ,)
            authors.add(surename + ", " + firstname);
        }
        return authors;
    }

    /**
     * simply result to new JSON
     *
     * @param result
     * @param printOriginal
     * @return
     * @throws JSONException
     */
    public JSONObject parseAuthorsToJSON(String result, boolean printOriginal) throws JSONException {
        JSONObject json = new JSONObject(result);
        JSONObject newJson = new JSONObject();
        newJson.put("doi", doi);

        if (printOriginal)
            Log.info(json.toString(4));

        JSONObject msg = json.getJSONObject("message");

        JSONArray arr = msg.getJSONArray("author");
        newJson.put("author", arr);

        // CrossRef make title as JSONArray
        Object title = msg.get("title");
        if (title instanceof JSONArray) {
            arr = (JSONArray) title;
            String t = arr.getString(0);
            for (int i = 1; i < arr.length(); i++) {
                t += " " + arr.getString(i);
            }
            newJson.put("title", t);
        } else {
            newJson.put("title", title);
        }

        newJson.put("publisher", msg.getString("publisher"));

        // "created": { "date-parts": [  [ 2014, 4, 10 ] ], ...},
        JSONArray date = msg.getJSONObject("created").getJSONArray("date-parts").getJSONArray(0);
        newJson.put("year", date.getString(0));

//        Log.info(newJson.toString(4));
        return newJson;
    }


    public static void process(PrintStream out, CrossRef... crossRefs) {
        String result;
        List<String> authors;
        String line;

        for (CrossRef crossRef : crossRefs) {
            try {
                result = crossRef.get();
                authors = crossRef.parseAuthors(result, false);

//                authors.forEach(System.out::println);
                line = String.join("\t", authors);
                line = crossRef.doi + "\t" + line;

                out.println(line);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        out.close();
    }

    public static void main(String[] args) {
        // one doi
        String doi = "10.1093/molbev/mss086";

        CrossRef crossRef = null;
        try {
            crossRef = new CrossRef(doi);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        CrossRef.process(System.out, crossRef);

        // many
//        String[] dois = new String[]{"10.1371/journal.pcbi.1003537", "10.3851/IMP2656", "10.1534/genetics.110.125260",
//                "10.1093/molbev/mss086", "10.1093/molbev/mst131", "10.1371/journal.pbio.0040088",
//                "10.1093/molbev/msi103", "10.1534/genetics.111.134627", "10.1371/journal.pcbi.1003919",
//                "10.1007/s00285-016-1034-0", "10.1093/sysbio/syy041", "10.1093/sysbio/syr087",
//                "10.1093/bioinformatics/btu770", "10.1093/molbev/msw064", "10.1080/10635150500433722",
//                "10.1101/038455", "10.1371/journal.pgen.1005421", "10.1093/molbev/msx186", "10.1101/020792",
//                "10.1038/s41559-018-0489-3", "10.7717/peerj.2406", "10.1098/rsif.2013.1106", "10.1073/pnas.1207965110",
//                "10.1371/journal.pcbi.1005130", "10.1534/genetics.116.193425", "10.1093/molbev/mst057",
//                "10.1093/bioinformatics/btu201", "10.1101/142570", "10.1093/molbev/mss258", "10.1093/sysbio/syq085",
//                "10.1093/molbev/msx307", "10.1093/sysbio/syv080", "10.1098/rsbl.2016.0273"};
//
//        try {
//            PrintStream writeDOI = new PrintStream(new File("dois.txt"));
//
//            List<CrossRef> crossRefList = new ArrayList<>();
//            for (String doi : dois) {
//                CrossRef crossRef = new CrossRef(doi);
//                crossRefList.add(crossRef);
//            }
//            CrossRef.process(writeDOI, crossRefList.toArray(new CrossRef[0]));
//
//            writeDOI.println();
//            writeDOI.close();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }


    }
}
