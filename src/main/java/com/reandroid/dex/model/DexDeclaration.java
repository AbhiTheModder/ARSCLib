/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.dex.model;

import com.reandroid.dex.common.AccessFlag;
import com.reandroid.dex.common.IdDefinition;
import com.reandroid.dex.common.Modifier;
import com.reandroid.dex.id.IdItem;
import com.reandroid.dex.key.Key;
import com.reandroid.dex.key.ProgramKey;
import com.reandroid.dex.key.TypeKey;
import com.reandroid.dex.program.AccessibleProgram;

import java.util.Iterator;

public abstract class DexDeclaration extends Dex implements AccessibleDex {

    public boolean uses(Key key) {
        if(getKey().equals(key)){
            return false;
        }
        return getDefinition().uses(key);
    }
    public boolean isAccessibleTo(TypeKey typeKey) {
        if(this.getDefining().equals(typeKey)){
            return true;
        }
        if(isInternal()) {
            return this.getPackageName().equals(typeKey.getPackageName());
        }
        return !isPrivate();
    }
    public boolean isAccessibleTo(DexClass dexClass) {
        DexClass myClass = getDexClass();
        TypeKey defining = dexClass.getDefining();
        if(!myClass.isAccessibleTo(defining)){
            return false;
        }
        if(myClass.getDefining().equals(defining)){
            return true;
        }
        return myClass == this || isAccessibleTo(defining);
    }
    public boolean hasAccessFlag(AccessFlag accessFlag) {
        return accessFlag.isSet(getAccessFlagsValue());
    }
    public boolean hasAccessFlag(AccessFlag flag1, AccessFlag flag2) {
        return hasAccessFlag(flag1) && hasAccessFlag(flag2);
    }
    public boolean hasAccessFlag(AccessFlag flag1, AccessFlag flag2, AccessFlag flag3) {
        return hasAccessFlag(flag1) &&
                hasAccessFlag(flag2) &&
                hasAccessFlag(flag3);
    }

    public abstract IdDefinition<?> getDefinition();
    public abstract ProgramKey getKey();
    public abstract IdItem getId();
    public abstract DexClass getDexClass();
    @Override
    public boolean isRemoved() {
        IdDefinition<?> definition = getDefinition();
        return definition == null || definition.isRemoved();
    }

    public Iterator<? extends Modifier> getAccessFlags(){
        return getDefinition().getAccessFlags();
    }
    public void addAccessFlag(AccessFlag accessFlag){
        getDefinition().addAccessFlag(accessFlag);
    }
    public void removeAccessFlag(AccessFlag accessFlag){
        getDefinition().removeAccessFlag(accessFlag);
    }

    public TypeKey getDefining(){
        return getKey().getDeclaring();
    }
    public DexLayout getDexLayout() {
        if(getClass() == DexClass.class){
            throw new RuntimeException(
                    "getDexFile() must be override for: " + getClass());
        }
        return getDexClass().getDexLayout();
    }
    public DexFile getDexFile() {
        return getDexLayout().getDexFile();
    }
    public DexDirectory getDexDirectory() {
        DexFile dexFile = getDexFile();
        if(dexFile != null){
            return dexFile.getDexDirectory();
        }
        return null;
    }
    @Override
    public DexClassRepository getClassRepository(){
        DexLayout dexLayout = getDexLayout();
        if(dexLayout != null){
            return dexLayout.getRootRepository();
        }
        return null;
    }
    public String getPackageName() {
        return getDefining().getPackageName();
    }
    public boolean isInSameFile(DexDeclaration dexDeclaration){
        if(dexDeclaration == null){
            return false;
        }
        if(dexDeclaration == this){
            return true;
        }
        DexLayout dexLayout = getDexLayout();
        if (dexLayout == null) {
            return false;
        }
        return dexLayout == dexDeclaration.getDexLayout();
    }
    public boolean isInSameDirectory(DexDirectory directory){
        return getDexDirectory() == directory;
    }

    @Override
    public AccessibleProgram getProgramElement() {
        return getDefinition();
    }

    @Override
    public int hashCode() {
        Key key = getKey();
        if(key != null){
            return key.hashCode();
        }
        return 0;
    }
    @Override
    public String toString() {
        return Modifier.toString(getAccessFlags()) + getKey();
    }
}
