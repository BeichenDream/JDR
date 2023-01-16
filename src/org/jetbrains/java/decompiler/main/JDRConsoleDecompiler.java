package org.jetbrains.java.decompiler.main;

import github.beichendream.java.decompiler.modules.renamer.ReStoreLineNumber;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JDRConsoleDecompiler extends ConsoleDecompiler {
  protected JDRConsoleDecompiler(File destination, Map<String, Object> options, IFernflowerLogger logger) {
    super(destination, options, logger);
  }

  public static JDRConsoleDecompiler getJDRConsoleDecompiler(String[] args) {

    Map<String, Object> mapOptions = new HashMap<>();
    List<File> sources = new ArrayList<>();
    List<File> libraries = new ArrayList<>();

    boolean isOption = true;
    for (int i = 0; i < args.length - 1; ++i) { // last parameter - destination
      String arg = args[i];

      if (isOption && arg.length() > 5 && arg.charAt(0) == '-' && arg.charAt(4) == '=') {
        String value = arg.substring(5);
        if ("true".equalsIgnoreCase(value)) {
          value = "1";
        }
        else if ("false".equalsIgnoreCase(value)) {
          value = "0";
        }

        mapOptions.put(arg.substring(1, 4), value);
      }
      else {
        isOption = false;

        if (arg.startsWith("-e=")) {
          addPath(libraries, arg.substring(3));
        }
        else {
          addPath(sources, arg);
        }
      }
    }

    if (sources.isEmpty()) {
      System.out.println("error: no sources given");
      return null;
    }

    File destination = new File(args[args.length - 1]);
    if (!destination.isDirectory()) {
      System.out.println("error: destination '" + destination + "' is not a directory");
      return null;
    }

    PrintStreamLogger logger = new PrintStreamLogger(System.out);
    JDRConsoleDecompiler decompiler = new JDRConsoleDecompiler(destination, mapOptions, logger);

    for (File library : libraries) {
      decompiler.addLibrary(library);
    }
    for (File source : sources) {
      decompiler.addSource(source);
    }

    return decompiler;
  }

  public static void deleteFiles(File file) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File f : files) {
        if (f.isDirectory()) {
          deleteFiles(f);
        }else {
          f.delete();
        }
      }
    }
  }

  protected File inJarFile;

  public static void main(String[] args) throws Throwable{
    System.out.println("JDR powered by BeichenDream");
    if (args.length != 2){
      System.err.println("java -jar jdr.jar rt.jar outdir");
      return;
    }
    File inJar = new File(args[0]);
    File outDir = new File(args[1]);

    if (!inJar.exists()){
      System.err.println("jar file does not exist");
      return;
    }

    if (outDir.exists()){
      deleteFiles(outDir);
    }else {
      outDir.mkdirs();
    }


    JDRConsoleDecompiler jdrConsoleDecompiler = getJDRConsoleDecompiler(new String[]{"-dgs=true",
      "-hdc=false",
      "-rbr=true"
      ,"-hes=false","-rsy=true","-nls=true","-mpm=90","-iib=true","-vac=true",
      inJar.getAbsolutePath(),outDir.getAbsolutePath()});
    if (jdrConsoleDecompiler != null) {
      jdrConsoleDecompiler.inJarFile = inJar;

      jdrConsoleDecompiler.decompileContext();
    }

  }

  @Override
  public void closeArchive(String path, String archiveName) {
    super.closeArchive(path, archiveName);

    String archiveClassesName = archiveName;
    if (archiveClassesName.endsWith(".jar")) {
      archiveClassesName = archiveClassesName.substring(0, archiveClassesName.length() - 4);
    }
    String archiveSourceName = archiveClassesName + "_source.jar";
    archiveClassesName = archiveClassesName + "_classes.jar";

    File file = new File(getAbsolutePath(path), archiveClassesName);

    File sourceFile = new File(getAbsolutePath(path), archiveName);

    sourceFile.renameTo(new File(getAbsolutePath(path), archiveSourceName));


    if (file.exists()){
      file.delete();
    }

    try {
      ReStoreLineNumber.start(inJarFile,file);
    } catch (Exception e) {
      e.printStackTrace();
    }


  }
}
