package beast.app.packagemanager;

import beast.core.Citation;
import beast.core.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Walter Xie
 */
public class CitedClass {

    protected final String className;
    protected String description = "";
    protected List<Citation> citations = new ArrayList<>();


    public CitedClass(String className, List<Citation> citations) {
        this.className = className;
        this.citations.addAll(citations);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCitations() {
        String citaStr = "";
        for (Citation citation : citations) {
            citaStr += citation.value() + "\n";
            // print DOI
            if (citation.DOI().length() > 0) {
                citaStr += citation.DOI() + "\n";
            }
        }
        return citaStr;
    }

    public List<String> getCitations(String delimiter) {
        List<String> cL = new ArrayList<>();
        for (Citation citation : citations) {
            // rm all \n \t
            String simpleCi = citation.value().replaceAll("\n", "").replaceAll("\t", "");
            // replace 2 spaces to 1
            simpleCi = simpleCi.replaceAll("  ", " ");
            cL.add(citation.DOI() + delimiter + simpleCi);
        }
        return cL;
    }

    public Set<String> getDOIs() {
        Set<String> dois = new HashSet<>();
        for (Citation citation : citations) {
            // print DOI
            if (citation.DOI().length() > 0) {
                dois.add(citation.DOI());
            } else {
                Log.warning("No DOI found in " + citation.value() +
                        " !\n Class name = " + className);
            }
        }
        return dois;
    }
}
