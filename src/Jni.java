
public class		Jni{
	public native byte[] mmap(String s);
	public native boolean open(String s);
	public native boolean mkdir(String name);
	public native boolean fork_exec_avisplit(String chunksDir,
						 String inputFile,
						 String chunkSize);
	public native boolean fork_exec_avimerge(String mergeDir,
						 String mergeOutput,
						 String input1,
						 String input2);
}
