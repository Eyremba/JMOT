package net.contra.obfuscator.trans;

import com.sun.org.apache.bcel.internal.classfile.Field;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.generic.*;
import net.contra.obfuscator.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FieldNameObfuscator implements ITransformer {
    private final LogHandler Logger = new LogHandler("FieldNameObfuscator");
    //ClassName, <OldSig, NewSig>
    private final Map<String, ArrayList<RenamePair>> ChangedFields = new HashMap<String, ArrayList<RenamePair>>();
    private String Location = "";
    private JarLoader LoadedJar;

    public FieldNameObfuscator(String loc) {
        Location = loc;
    }

    public void Load() {
        LoadedJar = new JarLoader(Location);
    }

    public void Transform() {
        //We rename methods
        for (ClassGen cg : LoadedJar.ClassEntries.values()) {
           ArrayList<RenamePair> NewFields = new ArrayList<RenamePair>();
            if (cg.isAbstract()) continue;
            for (Field field : cg.getFields()) {
                if (field.isInterface() || field.isAbstract())
                    continue;
                FieldGen fg = new FieldGen(field, cg.getConstantPool());
                String newName = Misc.getRandomString(4);
                fg.setName(newName);
                cg.replaceField(field, fg.getField());
                RenamePair newPair = new RenamePair(field.getName(), field.getSignature(), fg.getName());
                NewFields.add(newPair);
                Logger.Log("Obfuscating Field Names -> Class: " + cg.getClassName() + " Field: " + field.getName());
            }
            ChangedFields.put(cg.getClassName(), NewFields);
        }
        //We fix all of the field calls
        for (ClassGen cg : LoadedJar.ClassEntries.values()) {
            for (Method method : cg.getMethods()) {
                MethodGen mg = new MethodGen(method, cg.getClassName(), cg.getConstantPool());
                InstructionList list = mg.getInstructionList();
                if (list == null) continue;
                Logger.Log("Fixing Field Calls -> Class: " + cg.getClassName() + " Method: " + method.getName());
                InstructionHandle[] handles = list.getInstructionHandles();
                for (InstructionHandle handle : handles) {
                    if (BCELMethods.isFieldInvoke(handle.getInstruction())) {
                        String clazz = BCELMethods.getFieldInvokeClassName(handle.getInstruction(), cg.getConstantPool()).trim().replace(" ", "");
                        String fname = BCELMethods.getFieldInvokeName(handle.getInstruction(), cg.getConstantPool()).trim().replace(" ", "");
                        String fsig = BCELMethods.getFieldInvokeSignature(handle.getInstruction(), cg.getConstantPool()).trim().replace(" ", "");

                        if (!ChangedFields.containsKey(clazz)) continue;
                        for(RenamePair pair : ChangedFields.get(clazz)){
                            if(pair.OldName.equals(fname) && pair.OldSignature.equals(fsig)){
                                int index = cg.getConstantPool().addFieldref(clazz, pair.NewName, fsig);
                                handle.setInstruction(BCELMethods.getNewFieldInvoke(handle.getInstruction(), index));
                            }
                        }
                    }
                }
                list.setPositions();
                mg.setInstructionList(list);
                mg.setMaxLocals();
                mg.setMaxStack();
                mg.removeLineNumbers();
                cg.replaceMethod(method, mg.getMethod());
            }
        }
    }

    public void Dump() {
        LoadedJar.Save(Location.replace(".jar", "-new.jar"));
    }
}