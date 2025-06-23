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
package com.reandroid.dex.data;

import com.reandroid.arsc.base.Block;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.dex.base.Ule128Item;
import com.reandroid.dex.common.*;
import com.reandroid.dex.id.ClassId;
import com.reandroid.dex.id.IdItem;
import com.reandroid.dex.key.*;
import com.reandroid.dex.pool.DexSectionPool;
import com.reandroid.dex.sections.Section;
import com.reandroid.dex.sections.SectionList;
import com.reandroid.dex.sections.SectionType;
import com.reandroid.dex.smali.SmaliRegion;
import com.reandroid.dex.smali.model.Smali;
import com.reandroid.dex.smali.model.SmaliDef;
import com.reandroid.utils.ObjectsUtil;
import com.reandroid.utils.collection.*;

import java.io.IOException;
import java.util.Iterator;

public abstract class Def<T extends IdItem> extends FixedDexContainerWithTool implements
        IdDefinition<T>, EditableItem, Comparable<Def<T>>, SmaliRegion,
        DefIndex, IdUsageIterator {

    private final SectionType<T> sectionType;
    private final Ule128Item relativeId;
    private final Ule128Item accessFlags;
    private T mDefId;
    private int mCachedIndex;
    private boolean mCachedIndexUpdated;
    private HiddenApiFlagValue hiddenApiFlagValue;
    
    public Def(int childesCount, SectionType<T> sectionType) {
        super(childesCount + 2);
        this.sectionType = sectionType;
        this.relativeId = new Ule128Item(true);
        this.accessFlags = new Ule128Item();
        addChild(0, relativeId);
        addChild(1, accessFlags);
    }
    @Override
    public AnnotationSetKey getAnnotation() {
        return AnnotationSetKey.create(
                ComputeIterator.of(getAnnotationItemBlocks(), AnnotationItem::getKey));
    }
    @Override
    public void setAnnotation(AnnotationSetKey annotationSet) {
        if (!ObjectsUtil.equals(getAnnotation(), annotationSet)) {
            clearAnnotations();
            writeAnnotation(annotationSet);
        }
    }
    @Override
    public void clearAnnotations() {
        writeAnnotation(AnnotationSetKey.empty());
    }
    private boolean hasAnnotationSetBlocks() {
        return getAnnotationItemBlocks().hasNext();
    }
    private Iterator<AnnotationItem> getAnnotationItemBlocks() {
        AnnotationsDirectory directory = getAnnotationsDirectory();
        if (directory != null) {
            return ExpandIterator.of(directory
                    .getAnnotations(this));
        }
        return EmptyIterator.of();
    }
    private void writeAnnotation(AnnotationSetKey key) {
        if (key == null || key.isEmpty()) {
            if (hasAnnotationSetBlocks()) {
                getOrCreateUniqueAnnotationsDirectory().remove(this);
            }
        } else {
            getOrCreateUniqueAnnotationsDirectory().addAnnotation(this, key);
        }
    }

    @Override
    public Iterator<? extends Modifier> getModifiers() {
        return CombiningIterator.two(getAccessFlags(), getHiddenApiFlags());
    }
    public Iterator<HiddenApiFlag> getHiddenApiFlags(){
        HiddenApiFlagValue flagValue = getHiddenApiFlagValue();
        if(flagValue != null) {
            return flagValue.iterator();
        }
        return EmptyIterator.of();
    }
    public void addHiddenApiFlags(Iterator<HiddenApiFlag> iterator) {
        while (iterator.hasNext()) {
            addHiddenApiFlag(iterator.next());
        }
    }
    public void addHiddenApiFlag(HiddenApiFlag flag) {
        if (flag != null) {
            HiddenApiFlagValue flagValue = getOrCreateHiddenApiFlagValue();
            flagValue.add(flag);
        }
    }
    public void removeHiddenApiFlag(HiddenApiFlag flag) {
        if (flag != null) {
            HiddenApiFlagValue flagValue = getHiddenApiFlagValue();
            if (flagValue != null) {
                flagValue.remove(flag);
            }
        }
    }
    public void removeHiddenApiFlags() {
        HiddenApiFlagValue flagValue = getHiddenApiFlagValue();
        if (flagValue != null) {
            flagValue.clear();
        }
    }
    public HiddenApiFlagValue getOrCreateHiddenApiFlagValue() {
        HiddenApiFlagValue flagValue = getHiddenApiFlagValue();
        if (flagValue == null) {
            Section<HiddenApiRestrictions> section = getOrCreateSection(SectionType.HIDDEN_API);
            HiddenApiRestrictions restrictions = section.get(0);
            if (restrictions == null) {
                restrictions = section.createItem();
            }
            flagValue = restrictions.getFlagValue(getKey());
        }
        return flagValue;
    }
    public HiddenApiFlagValue getHiddenApiFlagValue() {
        return hiddenApiFlagValue;
    }
    public void linkHiddenApiFlagValueInternal(HiddenApiFlagValue hiddenApiFlagValue) {
        this.hiddenApiFlagValue = hiddenApiFlagValue;
    }

    public void removeSelf(){
        DefArray<Def<T>> array = getParentArray();
        if(array != null){
            array.remove(this);
        }
    }
    @Override
    public boolean isRemoved() {
        if (getParent() == null) {
            return true;
        }
        T id = getId();
        return id == null || id.isRemoved();
    }
    void onRemove(){
        HiddenApiFlagValue flagValue = getHiddenApiFlagValue();
        if (flagValue != null) {
            this.linkHiddenApiFlagValueInternal(null);
            flagValue.removeSelf();
        }
        mCachedIndexUpdated = true;
        mDefId = null;
        relativeId.set(0);
    }
    @Override
    public abstract ProgramKey getKey();

    public void setKey(Key key){
        setItem(key);
    }

    public Iterator<AnnotationItemKey> getAnnotationKeys() {
        Iterator<AnnotationSetKey> iterator = getAnnotationSetKeys();
        if (!iterator.hasNext()) {
            return EmptyIterator.of();
        }
        return new IterableIterator<AnnotationSetKey, AnnotationItemKey>(iterator) {
            @Override
            public Iterator<AnnotationItemKey> iterator(AnnotationSetKey element) {
                return element.iterator();
            }
        };
    }
    public Iterator<AnnotationSetKey> getAnnotationSetKeys() {
        return ComputeIterator.of(getAnnotationSets(true), AnnotationSet::getKey);
    }
    public Iterator<AnnotationSet> getAnnotationSets(boolean skipEmpty){
        AnnotationsDirectory directory = getAnnotationsDirectory();
        if(directory == null) {
            return EmptyIterator.of();
        }
        Iterator<AnnotationSet> iterator = directory.getAnnotations(this);
        if(!skipEmpty || !iterator.hasNext()) {
            return iterator;
        }
        return FilterIterator.of(iterator, annotationSet -> !annotationSet.isEmpty());
    }
    public AnnotationsDirectory getAnnotationsDirectory(){
        ClassId classId = getClassId();
        if (classId != null) {
            return classId.getAnnotationsDirectory();
        }
        return null;
    }
    public AnnotationsDirectory getOrCreateUniqueAnnotationsDirectory(){
        ClassId classId = getClassId();
        if(classId != null){
            return classId.getOrCreateUniqueAnnotationsDirectory();
        }
        return null;
    }
    public ClassId getClassId() {
        DefArray<Def<T>> array = getParentArray();
        if(array != null){
            ClassId classId = array.getClassId();
            if(classId != null){
                return classId;
            }
        }
        SectionList sectionList = getSectionList();
        if(sectionList == null || sectionList.isReading()){
            return null;
        }
        TypeKey defining = getDefining();
        if(defining == null){
            return null;
        }
        DexSectionPool<ClassId> pool = getPool(SectionType.CLASS_ID);
        if(pool == null){
            return null;
        }
        ClassId classId = pool.get(defining);
        if(classId == null) {
            return null;
        }
        ClassData classData = getClassData();
        if(classData == null){
            return null;
        }
        classData.setClassId(classId);
        return classId;
    }
    public TypeKey getDefining(){
        Key key = getKey();
        if(key != null){
            return key.getDeclaring();
        }
        return null;
    }
    public int getRelativeIdValue() {
        return relativeId.get();
    }
    @Override
    public int getAccessFlagsValue() {
        return accessFlags.get();
    }
    public void setAccessFlagsValue(int value) {
        accessFlags.set(value);
    }
    @Override
    public T getId(){
        return mDefId;
    }
    void setItem(Key key) {
        T item = getId();
        if(item != null && key.equals(item.getKey())){
            return;
        }
        item = getOrCreateSection(sectionType).getOrCreate(key);
        setItem(item);
    }
    void setItem(T item) {
        this.mDefId = item;
        updateIndex();
    }

    @Override
    public int getDefinitionIndex() {
        if(!mCachedIndexUpdated){
            mCachedIndexUpdated = true;
            mCachedIndex = calculateDefinitionIndex();
        }
        return mCachedIndex;
    }
    private int calculateDefinitionIndex(){
        DefArray<?> parentArray = getParentArray();
        if(parentArray != null){
            Def<?> previous = parentArray.get(getIndex() - 1);
            if(previous != null){
                return getRelativeIdValue() + previous.getDefinitionIndex();
            }
        }
        return relativeId.get();
    }
    private int getPreviousIdIndex() {
        DefArray<?> parentArray = getParentArray();
        if(parentArray != null){
            Def<?> previous = parentArray.get(getIndex() - 1);
            if(previous != null){
                return previous.getDefinitionIndex();
            }
        }
        return 0;
    }
    private ClassData getClassData(){
        DefArray<Def<T>> array = getParentArray();
        if(array != null){
            return array.getClassData();
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    private DefArray<Def<T>> getParentArray() {
        return (DefArray<Def<T>>) getParent();
    }

    @Override
    public void onReadBytes(BlockReader reader) throws IOException {
        super.onReadBytes(reader);
        cacheItem();
    }
    private void cacheItem(){
        this.mDefId = getSectionItem(sectionType, getDefinitionIndex());
        if(this.mDefId != null){
            this.mDefId.addUsageType(IdItem.USAGE_DEFINITION);
        }
    }
    @Override
    protected void onRefreshed() {
        super.onRefreshed();
        updateIndex();
        updateHiddenApiFlag();
    }
    private void updateIndex(){
        resetIndex();
        T item = this.mDefId;
        item = item.getReplace();
        this.mDefId = item;
        int index = getPreviousIdIndex();
        index = item.getIndex() - index;
        relativeId.set(index);
        item.addUsageType(IdItem.USAGE_DEFINITION);
    }
    void resetIndex(){
        mCachedIndexUpdated = false;
    }
    private void updateHiddenApiFlag() {
        HiddenApiFlagValue hiddenApiFlagValue = this.hiddenApiFlagValue;
        if (hiddenApiFlagValue != null && hiddenApiFlagValue.isRemoved()) {
            this.hiddenApiFlagValue = null;
        }
    }

    public void replaceKeys(Key search, Key replace){
        Key key = getKey();
        Key key2 = key.replaceKey(search, replace);
        if(key != key2){
            setItem(key2);
        }
    }

    @Override
    public void editInternal(Block user) {

    }

    public Iterator<IdItem> usedIds(){
        return SingleIterator.of(getId());
    }

    public void merge(Def<?> def){
        setItem(def.getKey());
        setAccessFlagsValue(def.getAccessFlagsValue());
        HiddenApiFlagValue flagValue = def.getHiddenApiFlagValue();
        if (flagValue != null) {
            addHiddenApiFlag(flagValue.getRestriction());
            addHiddenApiFlag(flagValue.getDomain());
        }
    }
    public abstract void fromSmali(Smali smali);
    public abstract SmaliDef toSmali();

    @Override
    public int compareTo(Def<T> other) {
        if(other == null){
            return -1;
        }
        return SectionTool.compareIdx(getId(), other.getId());
    }
}
