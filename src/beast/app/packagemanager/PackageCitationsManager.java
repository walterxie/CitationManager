package beast.app.packagemanager;

import beast.app.util.Arguments;
import beast.core.util.Log;
import beast.util.Package;
import beast.util.PackageDependency;
import beast.util.PackageManager;
import beast.util.PackageVersion;

import java.io.IOException;
import java.util.*;

/**
 * List all citations from locally installed BEAST 2 packages.
 * Usage: PackageCitations [-instAll]
 *     -instAll use PackageManager to update/install all packages (optional)
 * @see PackageCitations
 *
 * @author Walter Xie
 */
public class PackageCitationsManager {

    // use PackageManager to update/install all packages
    private static void installOrUpdateAllPackages(Map<String, Package> packageMap) throws IOException {
        Log.info.println("\nStart update/install all packages ...\n");
        for (Package aPackage : packageMap.values()) {
            Map<Package, PackageVersion> packagesToInstall = new HashMap<>();
            // always latest version
            packagesToInstall.put(aPackage, aPackage.getLatestVersion());
            try {
                // Populate given map with versions of packages to install which satisfy dependencies
                PackageManager.populatePackagesToInstall(packageMap, packagesToInstall);
            } catch (PackageManager.DependencyResolutionException ex) {
                Log.err("Installation aborted: " + ex.getMessage());
            }
            // Look through packages to be installed,
            // and uninstall any that are already installed but not match the version that is to be installed.
            PackageManager.prepareForInstall(packagesToInstall, false, null);
            // Download and install specified versions of packages
            Map<String, String> dirs = PackageManager.installPackages(packagesToInstall, false, null);
            if (dirs.size() == 0) {
                Log.info.println("Skip installed latest version package " + aPackage + " " + aPackage.getLatestVersion() + ".");
            } else {
                for (String pkgName : dirs.keySet())
                    Log.info.println("Package " + pkgName + " is installed in " + dirs.get(pkgName) + ".");
            }
        }
    }

    //find all installed and available packages
    private static Map<String, Package> getInstalledAvailablePackages() {
        // String::compareToIgnoreCase
        Map<String, Package> packageMap = new TreeMap<>(Comparator.comparing(String::toLowerCase));
        try {
            PackageManager.addInstalledPackages(packageMap);
            PackageManager.addAvailablePackages(packageMap);
        } catch (PackageManager.PackageListRetrievalException e) {
            Log.warning.println(e.getMessage());
            if (e.getCause() instanceof IOException)
                Log.warning.println(PackageManager.NO_CONNECTION_MESSAGE);
            return null;
        }
        Log.info.println("Find installed and available " + packageMap.size() + " packages.");
        return packageMap;
    }


    // only work for BEASTObject
    public static void main(String[] args) throws IOException {
        Arguments arguments = new Arguments(
                new Arguments.Option[]{
                        new Arguments.Option("instAll",
                                "Be careful, it will update/install all available packages. (optional)"),
                });

        try {
            arguments.parseArguments(args);
        } catch (Arguments.ArgumentException e) {
            e.printStackTrace();
        }

        //****** find all installed and available packages ******//
        Map<String, Package> packageMap = getInstalledAvailablePackages();
        if (packageMap == null) return;

        //****** update/install all packages ******//
        if (arguments.hasOption("instAll"))
            installOrUpdateAllPackages(packageMap);

        //****** process all citations ******//
        ProcessedPackage processedPackage = new ProcessedPackage(packageMap, false);
        int cc = processedPackage.getTotalCitation();
        Map<String, PackageCitations> processedPkgMap = processedPackage.getProcessedPkgMap();

        Log.info.println("====== Summary ======\n");
        Log.info.println("Find " + packageMap.size() + " BEAST packages, processed " + processedPkgMap.size() + ".");
        Log.info.println("Find total " + cc + " cited BEAST classes. \n");

        //****** save all citations to JSON ******//
//        try {
//            JSONObject citations = processedPackage.getJSONUniqueDOIs();
//            processedPackage.writeToFile(citations);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//        Set<DOIMapping> uniqDOIs = processedPackage.getUniqueDOIs();
//
//        for (DOIMapping doiMapping : uniqDOIs) {
//
//            Log.info.println(doiMapping.getName());
//        }

    }

    // process all citations given all installed packages
    public static class ProcessedPackage {
        // key is package name
        private Map<String, Package> packageMap;
        private int totalCitation = 0;
        // key is package name
        private Map<String, PackageCitations> processedPkgMap;

        // if verbose is false, print tab-delimited result
        public ProcessedPackage(Map<String, Package> packageMap, boolean verbose) throws IOException {
            this.packageMap = packageMap;
            process(verbose);
        }

        private void process(boolean verbose) throws IOException {
            processedPkgMap = new TreeMap<>(Comparator.comparing(String::toLowerCase));
            for (Map.Entry<String, Package> entry : packageMap.entrySet()) {
                Package pkg = entry.getValue();
                // process depended packages first
                Set<PackageDependency> dependencies = pkg.getDependencies(pkg.getLatestVersion());
                for (PackageDependency dependency : dependencies) {
                    Package depPkg = packageMap.get(dependency.dependencyName);
                    totalCitation += processCitations(depPkg, processedPkgMap, verbose);
                }
                totalCitation += processCitations(pkg, processedPkgMap, verbose);

                cleanClassPath(processedPkgMap);
                //System.out.println(System.getProperty("java.class.path"));
            }
        }

        // process citations for pkg and add name to processedPkgMap
        private int processCitations(Package pkg, Map<String, PackageCitations> processedPkgMap, boolean verbose) throws IOException {
            if (processedPkgMap.containsKey(pkg.getName())) {
                // if processed, do nothing except to add jar to class path
                PackageCitations packageCitations = processedPkgMap.get(pkg.getName());
                packageCitations.addJarFilesToClassPath();
                return 0;
            } else {
                // if not processed
                if (verbose)
                    Log.info.println("====== Package " + (processedPkgMap.size() + 1) + " : " + pkg.getName() + " ======\n");

                PackageCitations packageCitations = new PackageCitations(pkg, verbose);
                processedPkgMap.put(pkg.getName(), packageCitations);
                // print cited class as well
                return packageCitations.printCitedClasses(verbose);
            }
        }

        private void cleanClassPath(Map<String, PackageCitations> processedPkgMap) {
            for (Map.Entry<String, PackageCitations> entry : processedPkgMap.entrySet()) {
                if (! (entry.getKey().equalsIgnoreCase("beast2") ||
                        entry.getKey().equalsIgnoreCase("beast")) ) {
                    PackageCitations packageCitations = entry.getValue();
                    packageCitations.removeJarFilesFromClassPath();
                }
            }
        }

        public int getTotalCitation() {
            return totalCitation;
        }

        public Map<String, PackageCitations> getProcessedPkgMap() {
            return processedPkgMap;
        }

        public Set<DOIMapping> getUniqueDOIs() {
            Set<DOIMapping> uniqDOIs = new HashSet<>();

            for (Map.Entry<String, PackageCitations> entry : processedPkgMap.entrySet()) {
                String pkgName = entry.getKey();
                PackageCitations packageCitations = entry.getValue();
                Map<String, Set<DOIMapping>> dois = packageCitations.getDOIs();

                for (Map.Entry<String, Set<DOIMapping>> entry2 : dois.entrySet()) {
                    String doi = entry2.getKey();
                    Set<DOIMapping> doiMappingSet = entry2.getValue();

                    uniqDOIs.addAll(doiMappingSet);
                }
            }

            Log.info.println("====== DOI ======\n");
            Log.info.println("Find " + uniqDOIs.size() + " unique DOIs.\n");
            return uniqDOIs;
        }

//        public JSONObject getJSONUniqueDOIs() throws JSONException {
//
//            Set<DOIMapping> uniqDOIs = getUniqueDOIs();
//
//            for (DOIMapping doiMapping : uniqDOIs) {
//
//
//            }
//
//            JSONObject citations = new JSONObject();
//            JSONArray arr = new JSONArray();
//            for (Map.Entry<String, PackageCitations> entry : processedPkgMap.entrySet()) {
//                String pkgName = entry.getKey();
//                PackageCitations packageCitations = entry.getValue();
//                Map<String, Set<String>> dois = packageCitations.getDOIs();
//
//                for (Map.Entry<String, Set<String>> entry2 : dois.entrySet()) {
//                    String doi = entry2.getKey();
//                    Set<String> classNames = entry2.getValue();
//
//                    JSONObject json = null;
//                    try {
//                        CrossRef crossRef = new CrossRef(doi);
//                        String result = crossRef.get();
//
//                        json = crossRef.parseAuthorsToJSON(result, false);
//
//                        json.put("package", pkgName);
//
//                        JSONArray ja = new JSONArray();
//                        for (String className : classNames) {
//                            JSONObject jo = new JSONObject();
//                            jo.put("class", className);
//                            ja.put(jo);
//                        }
//                        json.put("classes", ja);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//
//                    arr.put(json);
//                }
//            }
//            citations.put("citations", arr);
//
//            return citations;
//        }
//
//        public void writeToFile(JSONObject mainJO){
//            try {
//                FileWriter fileWriter = new FileWriter("citations.json");
//                fileWriter.write(mainJO.toString(4));
//                fileWriter.flush();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }

    }
}
