/** GMenCoD environment class */
class                   Env {
    /** Chunk size (should be overriden by command line argument) */
    public static final String  CHUNK_SIZE = "7";

    /** GMEncoD extention */
    public static final String  GM_EXTENTION = ".gm";

    /** Avi extention */
    public static final String  AVI_EXTENTION = ".avi";

    /** Worker Standart input prefix */
    public static final String  STDIN = "gmin-";

    /** Worker Standart output prefix */
    public static final String  STDOUT = "gmout-";

    /** Worker Standart error prefix */
    public static final String  STDERR = "gmerr-";

    /** Raw chunks prefix */
    public static final String  RAW_CHUNK = "raw-";

    /** Encoded chunks prefix */
    public static final String  ENCODED_CHUNK = "encoded-";

    /** Merged chunks prefix */
    public static final String  MERGED_CHUNK = "merged-";

    /** Splitted chunks directory */
    public static final String  RAW_CHUNK_DIR = "chunks/";

    /** Encoded chunks directory */
    public static final String  ENCODED_CHUNK_DIR = "encChunks/";


    /** GMEncoD working directory */
    public static final String  WORKING_DIR = "GMEncoD-";


    /** Usage */
    public static final String  USAGE =
        "Usage: java " +
        "GMEncoD <inputfile> <worker.jar> <machines> [<chunk_size>]";
};
