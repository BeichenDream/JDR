package github.beichendream.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeSourceMapper;
import org.jetbrains.java.decompiler.struct.ContextUnit;
import org.objectweb.asm.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class ReStoreLineNumber {


  public static void start(File src,File dest) throws Exception{
    try {
      BytecodeSourceMapper bytecodeSourceMapper = DecompilerContext.getBytecodeSourceMapper();
      Map<String, Map<String, Map<Integer, Integer>>> classesMaps = ContextUnit.ClassesLineNumbers;

      ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(dest));

      ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(src));
      ZipEntry zipEntry = null;
      while ((zipEntry = zipInputStream.getNextEntry())!= null) {
        String fileName = zipEntry.getName();
        zipOutputStream.putNextEntry(zipEntry);
        if (!zipEntry.isDirectory()){
          byte[] entryContent = zipInputStream.readAllBytes(); ;

          if (fileName.endsWith(".class")) {
            String className = fileName.substring(0, fileName.length() - ".class".length());
            Map<String, Map<Integer, Integer>> methodMaps = classesMaps.get(className);

            if (methodMaps != null) {

              ClassReader cr = new ClassReader(new ByteArrayInputStream(entryContent));
              ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
              cr.accept(new ClassInfoModifier(cw,className,methodMaps), ClassReader.SKIP_DEBUG);


              entryContent = cw.toByteArray();
            }else {
              System.out.println("not found class " + className);
            }

          }
          zipOutputStream.write(entryContent);
          zipOutputStream.closeEntry();
          zipOutputStream.flush();
        }
      }

      zipOutputStream.finish();
      zipOutputStream.close();
      zipInputStream.close();

    }catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public static class ClassInfoModifier extends ClassVisitor {
    private  Map<String, Map<Integer, Integer>> methodMaps;
    private String className;

    private Integer classCodeStartLineNumber;

    public ClassInfoModifier(ClassWriter cw,String className, Map<String, Map<Integer, Integer>> methodMaps) {
      super(Opcodes.ASM9, cw);
      this.methodMaps = methodMaps;
      this.className = className;

      int lastToken = className.lastIndexOf('/');
      String tmoClassName = className;
      if (lastToken != -1) {
        tmoClassName = className.substring(lastToken + 1);
      }
      visitSource(tmoClassName + ".java",null);

      classCodeStartLineNumber = ClassesProcessor.ClassCodeStartLine.getOrDefault(className,0);

      if (classCodeStartLineNumber == 0){
//        System.out.println("bad code: " + className);
      }else {
//        classCodeStartLineNumber += 1;
      }

    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor methodVisitor =  super.visitMethod(access, name, descriptor, signature, exceptions);

      String methodNameSignature = name + " " + descriptor;

      Map<Integer, Integer> lineNumberMaps = methodMaps.get(methodNameSignature);

//      String[] lineNumbers = new String[] {"loadPlugin","reloadPlugin","addPlugin","updatePluginServer","parseFunction","parseConfig","convert","insertPluginByID","insertPlugin","insertPlugin"};

      if (lineNumberMaps!= null) {
//        lineNumberMaps = new HashMap<>(methodMaps.get(methodNameSignature));


        lineNumberMaps = lineNumberMaps.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
          (oldValue, newValue) -> oldValue, LinkedHashMap::new));;


        if ( (name.equals("<init>") || name.equals("<cinit>")) && lineNumberMaps.size() <= 2){
          Iterator<Map.Entry<Integer,Integer>> tmpLineNumbers = lineNumberMaps.entrySet().iterator();
          Map.Entry<Integer,Integer> lineNumber1 = tmpLineNumbers.next();
          Map.Entry<Integer,Integer> lineNumber2 = null;
          if (tmpLineNumbers.hasNext()){
            lineNumber2 = tmpLineNumbers.next();
            if (lineNumber2.getKey() < lineNumber1.getKey()){
              lineNumber1 = lineNumber2;
            }
          }
          methodVisitor.visitLineNumber(lineNumber1.getValue() + classCodeStartLineNumber,new MyLabel(lineNumber1.getKey()));
        }else {

          for (Map.Entry<Integer,Integer> lineNumberEntry: lineNumberMaps.entrySet()) {
            methodVisitor.visitLineNumber(lineNumberEntry.getValue() + classCodeStartLineNumber,new MyLabel(lineNumberEntry.getKey()));
          }
        }

      }


      return methodVisitor;
    }

  }

  public static class MyLabel extends Label {
    public MyLabel(int pc){
      try {
        Field field = Label.class.getDeclaredField("bytecodeOffset");
        field.setAccessible(true);
        field.set(this,pc);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }
}