package beast.app.packagemanager;

/**
 * @author Walter Xie
 */
public class DOIMapping implements Comparable<DOIMapping> {
    public final String doi;
    public final String pkgName;
    public final String className;

    public DOIMapping(String doi, String pkgName, String className) {
        this.doi = doi;
        this.pkgName = pkgName;
        this.className = className;
    }

    public String toJSON() {
        return "\"package\": " + pkgName + ", " + "\"class\": " + className;
    }

    public String getName() {
        return doi + ", " + pkgName + ", " + className;
    }

    @Override
    public int compareTo(DOIMapping o) {
        return o.getName().compareTo(this.getName());
    }
}
