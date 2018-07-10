package beast.app.packagemanager;

import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.core.Citation;
import beast.core.Description;
import beast.core.util.Log;
import beast.util.Package;
import beast.util.PackageManager;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Print the citation(s) annotated in a class inherited from BEASTObject,
 * which is found from a given installed BEAST 2 package.
 * Note: The current @Citation is automatically inherited.
 * If the class has no @Citation but its parent class has,
 * then it will use the same citation as its parent class.
 *
 * @author Walter Xie
 */
public class PackageCitations {

    public final Package pkg;

    protected File[] libJarFile;
    protected Map<String, CitedClass> citedClassMap = new TreeMap<>();

    // give one package at a time
    public PackageCitations(Package pkg, boolean verbose) {
        this.pkg = pkg;
        try {
            libJarFile = guessLibJarFile(pkg);
            assert libJarFile != null;

            addJarFilesToClassPath();

            setCitedClassMap(libJarFile, verbose);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * add all jars to class path using {@link PackageManager#addURL(URL) addURL}
     * @throws IOException
     */
    public void addJarFilesToClassPath() throws IOException {
        for (File f: libJarFile)
            PackageManager.addURL(f.toURL());
    }

    /**
     * remove all jars from class path, to clean the name space for next analysis
     */
    public void removeJarFilesFromClassPath() {
        String separator = System.getProperty("path.separator");
        String classpath = System.getProperty("java.class.path");
        for (File f: libJarFile) {
            String fPath = f.toString();
            if (classpath.contains(fPath)) {
                classpath = classpath.replace(separator + fPath, "");
            }
        }
        System.setProperty("java.class.path", classpath);
    }

    /**
     * Add all cited classes from jar files in a {@link Package beast package} lib dir.
     * @param libJarFile jar files
     * @throws IOException
     */
    public void setCitedClassMap(File[] libJarFile, boolean verbose) throws IOException {
        for (File f: libJarFile) {
            if (verbose)
                Log.info.println("Load classes from : " + f + "");

            Map<String, CitedClass> tmp = getAllCitedClasses(f);
            // add all to the final map
            tmp.keySet().removeAll(citedClassMap.keySet());
            citedClassMap.putAll(tmp);
        }
        if (verbose) Log.info.println();
    }

    /**
     * get {@link CitedClass} {@link TreeMap}, where key is the class name.
     * @return
     */
    public Map<String, CitedClass> getCitedClassMap() {
        return citedClassMap;
    }

    /**
     * print all cited classes from a {@link Package beast package}.
     * @return the total number of cited classes
     * @throws IOException
     */
    public int printCitedClasses(boolean verbose) {
        if (verbose) {
            //        setCitedClassMap(libJarFile);
            for (Map.Entry<String, CitedClass> entry : getCitedClassMap().entrySet()) {
                Log.info.println(entry.getKey());
                CitedClass citedClass = entry.getValue();
                Log.info.println(citedClass.getCitations());
                Log.info.println("Description : " + citedClass.getDescription() + "\n");
            }
            Log.info.println("Find total " + getCitedClassMap().size() + " cited BEAST classes.");
            Log.info.println();
        } else {
            for (Map.Entry<String, CitedClass> entry : getCitedClassMap().entrySet()) {
                CitedClass citedClass = entry.getValue();
                for (String citation : citedClass.getCitations("\t")) {
                    Log.info.print(pkg.getName());
                    Log.info.print("\t" + entry.getKey());
                    Log.info.print("\t" + citation);
                    Log.info.println();
                }
            }
        }
        return getCitedClassMap().size();
    }


    /**
     * accumulate DOIs in the {@link Set} from all annotated {@link BEASTObject}s.
     * @return the unique DOIs
     */
    public Map<String, Set<DOIMapping>> getDOIs() {
        Map<String, Set<DOIMapping>> dois = new HashMap<>();
        for (Map.Entry<String, CitedClass> entry : getCitedClassMap().entrySet()) {
            Log.info.println(entry.getKey());
            CitedClass citedClass = entry.getValue();
            Set<String> tmpSet = citedClass.getDOIs();
            for (String doi : tmpSet) {
                DOIMapping doiMapping = new DOIMapping(doi, pkg.getName(), citedClass.className);
                if (dois.containsKey(doi)) {
                    Set<DOIMapping> doiMappingSet = dois.get(doi);
                    doiMappingSet.add(doiMapping);
                } else {
                    Set<DOIMapping> doiMappingSet = new HashSet<>();
                    doiMappingSet.add(doiMapping);
                    dois.put(doi, doiMappingSet);
                }
            }
        }
        Log.info.println("Find total unique " + dois.size() + " publications with DOI in " + pkg.getName() + ".\n");
        return dois;
    }

    /**
     * get a {@link Citation Citation} list from a beast class.
     * @see BEASTInterface#getCitationList()
     * @param beastClass
     * @return
     */
    public List<Citation> getCitationList(Class<?> beastClass) {
        final Annotation[] classAnnotations = beastClass.getAnnotations();
        List<Citation> citations = new ArrayList<>();
        for (final Annotation annotation : classAnnotations) {
            if (annotation instanceof Citation) {
                citations.add((Citation) annotation);
            }
            if (annotation instanceof Citation.Citations) {
                for (Citation citation : ((Citation.Citations) annotation).value()) {
                    citations.add(citation);
                }
            }
        }
        return citations;
    }

    /**
     * get a description from a beast class.
     * @see BEASTInterface#getDescription()
     * @param beastClass
     * @return
     */
    public String getDescription(Class<?> beastClass) {
        final Annotation[] classAnnotations = beastClass.getAnnotations();
        for (final Annotation annotation : classAnnotations) {
            if (annotation instanceof Description) {
                final Description description = (Description) annotation;
                return description.value();
            }
        }
        return "Not documented!!!";
    }

    // find all *.jar in lib, but exclude *.src.jar
    private File[] guessLibJarFile(Package pkg) throws IOException {
        // get dir where pkg is installed
        String dirName = PackageManager.getPackageDir(pkg, pkg.getLatestVersion(), false, null);

        // beast installed package path
        File libDir = new File(dirName + File.separator + "lib");
        if (!libDir.exists())
            throw new IOException("Cannot find package " + pkg.getName() + " in path " + dirName);

        // first guess: *.jar but exclude *.src.jar
        File[] libFiles = libDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.endsWith("src.jar"));
        if (libFiles == null || libFiles.length < 1)
            throw new IOException("Cannot find jar file in package " +  pkg.getName() + " in path " + dirName);

        return libFiles;
    }

    // find all cited classes from a jar file, key is class name
    private Map<String, CitedClass> getAllCitedClasses(File libFile) throws IOException {
        // find all *.class in the jar
        JarFile jarFile = new JarFile(libFile);
        Enumeration allEntries = jarFile.entries();

        Map<String, CitedClass> citedClassMap = new TreeMap<>();
        while (allEntries.hasMoreElements()) {
            JarEntry jarEntry = (JarEntry) allEntries.nextElement();
            String name = jarEntry.getName();
            // exclude tests, cern (colt.jar) and com (google) have troubles
            if ( name.endsWith(".class") && !(name.startsWith("test") || name.startsWith("cern") || name.startsWith("com")) ) {
                String className = name.replaceAll("/", "\\.");
                className = className.substring(0, className.lastIndexOf('.'));

//                if (!className.startsWith("beast"))
//                    System.out.println(className);
//                System.out.println(System.getProperty("java.class.path"));

                // making own child classloader
                // https://stackoverflow.com/questions/60764/how-should-i-load-jars-dynamically-at-runtime/60775#60775
                URLClassLoader child = new URLClassLoader(new URL[]{libFile.toURL()},
                        PackageCitations.class.getClassLoader());
                Class<?> beastClass = null;
                try {
                    beastClass = Class.forName(className, false, child);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new IOException(className + " cannot be loaded by ClassLoader !");
                }

                // no abstract classes
                if (!Modifier.isAbstract(beastClass.getModifiers()) &&
                        // must implement interface
                        (beastClass.isInterface() && PackageManager.hasInterface(BEASTObject.class, beastClass)) ||
                        // must be derived from class
                        (!beastClass.isInterface() && PackageManager.isSubclass(BEASTObject.class, beastClass))) {

                    List<Citation> citations = getCitationList(beastClass);
                    // add citations (if any)
                    if (citations.size() > 0) {
//                        System.out.println(className);
                        CitedClass citedClass = new CitedClass(className, citations);
                        String description = getDescription(beastClass);
                        // add description when having a citation
                        citedClass.setDescription(description);

                        citedClassMap.put(className, citedClass);
                    }
                }
            }
        }
        return citedClassMap;
    }

    private void toXML(){
        //TODO
    }

}
